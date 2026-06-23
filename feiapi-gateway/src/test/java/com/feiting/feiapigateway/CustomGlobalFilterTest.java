package com.feiting.feiapigateway;

import com.feiting.feiapicommon.service.InnerUserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapigateway.config.FeiapiGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 网关全局过滤器测试
 */
@DisplayName("CustomGlobalFilter 测试")
class CustomGlobalFilterTest {

    private CustomGlobalFilter createFilter(InnerUserInterfaceInfoService innerUserInterfaceInfoService) {
        CustomGlobalFilter filter = new CustomGlobalFilter(mock(ReactiveStringRedisTemplate.class), new FeiapiGatewayProperties());
        ReflectionTestUtils.setField(filter, "innerUserInterfaceInfoService", innerUserInterfaceInfoService);
        return filter;
    }

    private ServerWebExchange createExchange() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        return MockServerWebExchange.from(request);
    }

    @Test
    @DisplayName("下游响应 200 时确认预扣次数，不执行补偿")
    void shouldNotRollbackWhenResponseOk() {
        InnerUserInterfaceInfoService innerService = mock(InnerUserInterfaceInfoService.class);
        CustomGlobalFilter filter = createFilter(innerService);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = currentExchange -> {
            currentExchange.getResponse().setStatusCode(HttpStatus.OK);
            return currentExchange.getResponse().setComplete();
        };

        filter.handleResponse(exchange, chain, 1L, 2L).block();

        verify(innerService, never()).rollbackInvokeCount(anyLong(), anyLong());
    }

    @Test
    @DisplayName("下游响应非 200 时返还预扣次数")
    void shouldRollbackWhenResponseNotOk() {
        InnerUserInterfaceInfoService innerService = mock(InnerUserInterfaceInfoService.class);
        when(innerService.rollbackInvokeCount(1L, 2L)).thenReturn(true);
        CustomGlobalFilter filter = createFilter(innerService);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = currentExchange -> {
            currentExchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return currentExchange.getResponse().setComplete();
        };

        filter.handleResponse(exchange, chain, 1L, 2L).block();

        verify(innerService, times(1)).rollbackInvokeCount(1L, 2L);
    }

    @Test
    @DisplayName("下游响应异常时只返还一次预扣次数")
    void shouldRollbackOnceWhenResponseError() {
        InnerUserInterfaceInfoService innerService = mock(InnerUserInterfaceInfoService.class);
        when(innerService.rollbackInvokeCount(1L, 2L)).thenReturn(true);
        CustomGlobalFilter filter = createFilter(innerService);
        ServerWebExchange exchange = createExchange();
        GatewayFilterChain chain = currentExchange -> Mono.error(new RuntimeException("调用失败"));

        try {
            filter.handleResponse(exchange, chain, 1L, 2L).block();
        } catch (RuntimeException ignored) {
            // 测试关注补偿次数，异常继续向上传播符合响应链路行为。
        }

        verify(innerService, times(1)).rollbackInvokeCount(1L, 2L);
    }

    @Test
    @DisplayName("合法 targetHost 会改写为内部服务转发地址")
    void shouldRewriteSafeTargetHost() {
        CustomGlobalFilter filter = createFilter(mock(InnerUserInterfaceInfoService.class));
        ServerWebExchange exchange = createExchange();
        InterfaceInfo interfaceInfo = buildInterfaceInfo("http://feiapi-interface:8123");

        ServerWebExchange targetExchange = ReflectionTestUtils.invokeMethod(filter,
                "rewriteTargetExchange", exchange, interfaceInfo);

        URI targetUri = targetExchange.getRequest().getURI();
        assertThat(targetUri.toString()).isEqualTo("http://feiapi-interface:8123/api/test");
    }

    @Test
    @DisplayName("危险 targetHost 会拒绝转发")
    void shouldRejectUnsafeTargetHost() {
        CustomGlobalFilter filter = createFilter(mock(InnerUserInterfaceInfoService.class));
        ServerWebExchange exchange = createExchange();
        InterfaceInfo interfaceInfo = buildInterfaceInfo("http://127.0.0.1:8123");

        ServerWebExchange targetExchange = ReflectionTestUtils.invokeMethod(filter,
                "rewriteTargetExchange", exchange, interfaceInfo);

        assertThat(targetExchange).isNull();
    }

    /**
     * 构建接口信息测试对象。
     *
     * @param targetHost 真实后端服务地址
     * @return 接口信息
     */
    private InterfaceInfo buildInterfaceInfo(String targetHost) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(1L);
        interfaceInfo.setPath("/api/test");
        interfaceInfo.setTargetHost(targetHost);
        return interfaceInfo;
    }
}
