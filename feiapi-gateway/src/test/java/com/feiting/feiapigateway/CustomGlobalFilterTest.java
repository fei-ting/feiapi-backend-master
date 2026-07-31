package com.feiting.feiapigateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapicommon.service.InnerUserInterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.service.InnerInterfaceInvokeLogService;
import com.feiting.feiapicommon.service.InnerInterfaceInfoService;
import com.feiting.feiapicommon.service.InnerUserService;
import com.feiting.feiapigateway.config.FeiapiGatewayProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 网关全局过滤器测试
 */
@DisplayName("CustomGlobalFilter 测试")
class CustomGlobalFilterTest {

    /** 网关允许的最大签名请求体字节数 */
    private static final int MAX_REQUEST_BODY_BYTES = 65_535;

    private CustomGlobalFilter createFilter(InnerUserInterfaceInfoService innerUserInterfaceInfoService) {
        CustomGlobalFilter filter = new CustomGlobalFilter(mock(ReactiveStringRedisTemplate.class),
                new FeiapiGatewayProperties(), new ObjectMapper());
        ReflectionTestUtils.setField(filter, "innerUserInterfaceInfoService", innerUserInterfaceInfoService);
        ReflectionTestUtils.setField(filter, "innerInterfaceInvokeLogService", mock(InnerInterfaceInvokeLogService.class));
        return filter;
    }

    private CustomGlobalFilter createFilter(InnerUserInterfaceInfoService innerUserInterfaceInfoService,
                                            InnerInterfaceInvokeLogService innerInterfaceInvokeLogService) {
        CustomGlobalFilter filter = new CustomGlobalFilter(mock(ReactiveStringRedisTemplate.class),
                new FeiapiGatewayProperties(), new ObjectMapper());
        ReflectionTestUtils.setField(filter, "innerUserInterfaceInfoService", innerUserInterfaceInfoService);
        ReflectionTestUtils.setField(filter, "innerInterfaceInvokeLogService", innerInterfaceInvokeLogService);
        return filter;
    }

    private ServerWebExchange createExchange() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        return MockServerWebExchange.from(request);
    }

    /**
     * 验证声明长度超限时在任何业务查询前返回统一 413 响应。
     */
    @Test
    @DisplayName("声明请求体超限时直接返回 413 且不进入业务链路")
    void shouldRejectOversizedDeclaredBodyBeforeBusinessCalls() {
        InnerUserService userService = mock(InnerUserService.class);
        InnerInterfaceInfoService interfaceInfoService = mock(InnerInterfaceInfoService.class);
        CustomGlobalFilter filter = createBoundaryFilter(userService, interfaceInfoService);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/test")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(MAX_REQUEST_BODY_BYTES + 1))
                .body("x");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertRequestBodyTooLargeResponse(exchange);
        verifyNoInteractions(userService, interfaceInfoService, chain);
    }

    /**
     * 验证无声明长度的分块正文仍按实际聚合字节执行上限校验。
     */
    @Test
    @DisplayName("未知长度多数据块正文超限时返回 413")
    void shouldRejectOversizedChunkedBodyByActualBytes() {
        InnerUserService userService = mock(InnerUserService.class);
        InnerInterfaceInfoService interfaceInfoService = mock(InnerInterfaceInfoService.class);
        CustomGlobalFilter filter = createBoundaryFilter(userService, interfaceInfoService);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/test").build());
        byte[] firstChunk = new byte[32_768];
        byte[] secondChunk = new byte[32_768];
        ServerHttpRequestDecorator chunkedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.CONTENT_LENGTH);
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(firstChunk),
                        DefaultDataBufferFactory.sharedInstance.wrap(secondChunk));
            }
        };
        ServerWebExchange chunkedExchange = exchange.mutate().request(chunkedRequest).build();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(chunkedExchange, chain).block();

        assertRequestBodyTooLargeResponse(chunkedExchange);
        verifyNoInteractions(userService, interfaceInfoService, chain);
    }

    /**
     * 验证正文恰好达到上限时允许进入后续鉴权，而不是误报 413。
     */
    @Test
    @DisplayName("请求体恰好达到上限时继续进入鉴权")
    void shouldAllowBodyExactlyAtLimit() {
        InnerUserService userService = mock(InnerUserService.class);
        InnerInterfaceInfoService interfaceInfoService = mock(InnerInterfaceInfoService.class);
        CustomGlobalFilter filter = createBoundaryFilter(userService, interfaceInfoService);
        byte[] body = new byte[MAX_REQUEST_BODY_BYTES];
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/test")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, currentExchange -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(userService).getInvokeUser(null);
        verifyNoInteractions(interfaceInfoService);
    }

    /**
     * 创建只用于请求体边界测试的过滤器并注入关键业务依赖。
     *
     * @param userService          用户查询服务
     * @param interfaceInfoService 接口查询服务
     * @return 已注入测试依赖的过滤器
     */
    private CustomGlobalFilter createBoundaryFilter(InnerUserService userService,
                                                     InnerInterfaceInfoService interfaceInfoService) {
        CustomGlobalFilter filter = new CustomGlobalFilter(mock(ReactiveStringRedisTemplate.class),
                new FeiapiGatewayProperties(), new ObjectMapper());
        ReflectionTestUtils.setField(filter, "innerUserService", userService);
        ReflectionTestUtils.setField(filter, "innerInterfaceInfoService", interfaceInfoService);
        ReflectionTestUtils.setField(filter, "innerUserInterfaceInfoService", mock(InnerUserInterfaceInfoService.class));
        ReflectionTestUtils.setField(filter, "innerInterfaceInvokeLogService", mock(InnerInterfaceInvokeLogService.class));
        return filter;
    }

    /**
     * 断言网关请求体超限响应符合统一状态码和 JSON 契约。
     *
     * @param exchange 请求上下文
     */
    private void assertRequestBodyTooLargeResponse(ServerWebExchange exchange) {
        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        String responseBody = response.getBodyAsString().block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/json;charset=UTF-8");
        assertThat(responseBody)
                .isEqualTo("{\"code\":41300,\"data\":null,\"message\":\"请求体不能超过 65535 字节\"}");
    }

    @Test
    @DisplayName("下游响应 200 时确认预扣次数，不执行补偿")
    void shouldNotRollbackWhenResponseOk() {
        InnerUserInterfaceInfoService innerService = mock(InnerUserInterfaceInfoService.class);
        InnerInterfaceInvokeLogService logService = mock(InnerInterfaceInvokeLogService.class);
        when(logService.recordInvoke(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(true);
        CustomGlobalFilter filter = createFilter(innerService, logService);
        ServerWebExchange exchange = createExchange();
        InterfaceInfo interfaceInfo = buildInterfaceInfo("http://feiapi-interface:8123");
        GatewayFilterChain chain = currentExchange -> {
            currentExchange.getResponse().setStatusCode(HttpStatus.OK);
            return currentExchange.getResponse().setComplete();
        };

        filter.handleResponse(exchange, chain, 1L, interfaceInfo).block();

        verify(innerService, never()).rollbackInvokeCount(anyLong(), anyLong());
        verify(logService, times(1)).recordInvoke(eq(1L), eq(1L), eq("/api/test"), eq("GET"),
                eq(200), eq(true), anyLong());
    }

    @Test
    @DisplayName("下游响应非 200 时返还预扣次数")
    void shouldRollbackWhenResponseNotOk() {
        InnerUserInterfaceInfoService innerService = mock(InnerUserInterfaceInfoService.class);
        InnerInterfaceInvokeLogService logService = mock(InnerInterfaceInvokeLogService.class);
        when(innerService.rollbackInvokeCount(1L, 2L)).thenReturn(true);
        when(logService.recordInvoke(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(true);
        CustomGlobalFilter filter = createFilter(innerService, logService);
        ServerWebExchange exchange = createExchange();
        InterfaceInfo interfaceInfo = buildInterfaceInfo("http://feiapi-interface:8123");
        interfaceInfo.setId(2L);
        GatewayFilterChain chain = currentExchange -> {
            currentExchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return currentExchange.getResponse().setComplete();
        };

        filter.handleResponse(exchange, chain, 1L, interfaceInfo).block();

        verify(innerService, times(1)).rollbackInvokeCount(1L, 2L);
        verify(logService, times(1)).recordInvoke(eq(1L), eq(2L), eq("/api/test"), eq("GET"),
                eq(500), eq(false), anyLong());
    }

    @Test
    @DisplayName("下游响应异常时只返还一次预扣次数")
    void shouldRollbackOnceWhenResponseError() {
        InnerUserInterfaceInfoService innerService = mock(InnerUserInterfaceInfoService.class);
        InnerInterfaceInvokeLogService logService = mock(InnerInterfaceInvokeLogService.class);
        when(innerService.rollbackInvokeCount(1L, 2L)).thenReturn(true);
        when(logService.recordInvoke(anyLong(), anyLong(), anyString(), anyString(), anyInt(), anyBoolean(), anyLong()))
                .thenReturn(true);
        CustomGlobalFilter filter = createFilter(innerService, logService);
        ServerWebExchange exchange = createExchange();
        InterfaceInfo interfaceInfo = buildInterfaceInfo("http://feiapi-interface:8123");
        interfaceInfo.setId(2L);
        GatewayFilterChain chain = currentExchange -> Mono.error(new RuntimeException("调用失败"));

        try {
            filter.handleResponse(exchange, chain, 1L, interfaceInfo).block();
        } catch (RuntimeException ignored) {
            // 测试关注补偿次数，异常继续向上传播符合响应链路行为。
        }

        verify(innerService, times(1)).rollbackInvokeCount(1L, 2L);
        verify(logService, times(1)).recordInvoke(eq(1L), eq(2L), eq("/api/test"), eq("GET"),
                eq(500), eq(false), anyLong());
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
        interfaceInfo.setMethod("GET");
        interfaceInfo.setTargetHost(targetHost);
        return interfaceInfo;
    }
}
