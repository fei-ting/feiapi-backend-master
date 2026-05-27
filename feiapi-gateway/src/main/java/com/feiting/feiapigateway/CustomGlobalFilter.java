package com.feiting.feiapigateway;

import com.feiting.feiapiclientsdk.utils.SignUtils;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.service.InnerInterfaceInfoService;
import com.feiting.feiapicommon.service.InnerUserInterfaceInfoService;
import com.feiting.feiapicommon.service.InnerUserService;
import com.feiting.feiapigateway.config.FeiapiGatewayProperties;
import com.feiting.feiapigateway.utils.LogDesensitizeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 全局过滤器
 */
@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final long FIVE_MINUTES = 60 * 5L;
    private static final int NONCE_LENGTH = 32;
    private static final int INTERFACE_STATUS_ONLINE = 1;
    private static final String NONCE_KEY_PREFIX = "feiapi:nonce:";

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final FeiapiGatewayProperties feiapiGatewayProperties;

    @DubboReference
    private InnerUserInterfaceInfoService innerUserInterfaceInfoService;

    @DubboReference
    private InnerInterfaceInfoService innerInterfaceInfoService;

    @DubboReference
    private InnerUserService innerUserService;

    public CustomGlobalFilter(ReactiveStringRedisTemplate reactiveStringRedisTemplate,
                              FeiapiGatewayProperties feiapiGatewayProperties) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
        this.feiapiGatewayProperties = feiapiGatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();
        String path = feiapiGatewayProperties.getNormalizedInterfaceHost() + requestPath;
        String method = request.getMethod().toString();
        ServerHttpResponse response = exchange.getResponse();

        log.info("请求唯一标识: {}", request.getId());
        log.info("请求路径: {}", requestPath);
        log.info("请求方法: {}", method);
        log.info("请求参数: {}", LogDesensitizeUtils.toSafeQueryParams(request.getQueryParams()));
        log.info("请求来源地址: {}", getClientIp(request));

        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String sign = headers.getFirst("sign");
        String timestamp = headers.getFirst("timestamp");

        User invokeUser = null;
        try {
            invokeUser = innerUserService.getInvokeUser(accessKey);
        } catch (Exception e) {
            log.error("getInvokeUser error: {}", e.getMessage(), e);
        }
        if (invokeUser == null) {
            return handleNoAuth(response);
        }

        if (!isValidNonce(nonce)) {
            return handleNoAuth(response);
        }

        Long requestTimestamp = parseTimestamp(timestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        if (requestTimestamp == null || Math.abs(currentTime - requestTimestamp) > FIVE_MINUTES) {
            return handleNoAuth(response);
        }

        User finalInvokeUser = invokeUser;
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(response.bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);

                    String secretKey = finalInvokeUser.getSecretKey();
                    String serverSign = SignUtils.getSign(secretKey, method, requestPath, nonce, timestamp, body);
                    if (sign == null || !sign.equals(serverSign)) {
                        return handleNoAuth(response);
                    }

                    return tryConsumeNonce(accessKey, nonce)
                            .flatMap(consumed -> {
                                if (!consumed) {
                                    return handleNoAuth(response);
                                }

                                InterfaceInfo interfaceInfo;
                                try {
                                    interfaceInfo = innerInterfaceInfoService.getInterfaceInfo(path, method);
                                } catch (Exception e) {
                                    log.error("getInterfaceInfo error: {}", e.getMessage(), e);
                                    return handleInvokeError(response);
                                }
                                if (interfaceInfo == null
                                        || !Integer.valueOf(INTERFACE_STATUS_ONLINE).equals(interfaceInfo.getStatus())) {
                                    return handleInvokeError(response);
                                }

                                Long userId = finalInvokeUser.getId();
                                Long interfaceInfoId = interfaceInfo.getId();
                                try {
                                    innerUserInterfaceInfoService.leftNumIsEnough(userId, interfaceInfoId);
                                } catch (Exception e) {
                                    log.error("leftNumIsEnough error: {}", e.getMessage(), e);
                                    return handleInvokeError(response);
                                }

                                DataBufferFactory bufferFactory = response.bufferFactory();
                                ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return Flux.defer(() -> Flux.just(bufferFactory.wrap(bodyBytes)));
                                    }
                                };

                                return handleResponse(exchange.mutate().request(decoratedRequest).build(), chain, userId, interfaceInfoId);
                            })
                            .onErrorResume(e -> {
                                log.error("tryConsumeNonce error: {}", e.getMessage(), e);
                                return handleInvokeError(response);
                            });
                });
    }

    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * 处理响应
     */
    public Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain, long userId, long interfaceInfoId) {
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            HttpStatus statusCode = originalResponse.getStatusCode();
            if (statusCode == HttpStatus.OK) {
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", body instanceof Flux);
                        if (body instanceof Flux) {
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            return super.writeWith(
                                    fluxBody.map(dataBuffer -> {
                                        try {
                                            innerUserInterfaceInfoService.invokeCount(userId, interfaceInfoId);
                                        } catch (Exception e) {
                                            log.error("invokeCount error: {}", e.getMessage(), e);
                                        }

                                        byte[] content = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(content);
                                        DataBufferUtils.release(dataBuffer);

                                        HttpStatus currentStatusCode = getStatusCode();
                                        log.info("接口响应完成, status: {}, bodyLength: {} bytes",
                                                currentStatusCode == null ? "UNKNOWN" : currentStatusCode.value(),
                                                content.length);
                                        return bufferFactory.wrap(content);
                                    }));
                        }
                        log.error("<--- {} 响应 code 异常", getStatusCode());
                        return super.writeWith(body);
                    }
                };
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            return chain.filter(exchange);
        } catch (Exception e) {
            log.error("网关处理响应异常 {}", e.getMessage(), e);
            return chain.filter(exchange);
        }
    }

    private Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    private Mono<Void> handleInvokeError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return response.setComplete();
    }

    private boolean isValidNonce(String nonce) {
        if (nonce == null || nonce.length() != NONCE_LENGTH) {
            return false;
        }
        for (int i = 0; i < nonce.length(); i++) {
            if (!Character.isLetterOrDigit(nonce.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private Long parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return null;
        }
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("timestamp 格式非法: {}", timestamp);
            return null;
        }
    }

    private String getClientIp(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        String xForwardedFor = headers.getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = headers.getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getHostString();
        }
        return "unknown";
    }

    private Mono<Boolean> tryConsumeNonce(String accessKey, String nonce) {
        String nonceKey = NONCE_KEY_PREFIX + accessKey + ":" + nonce;
        return reactiveStringRedisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", Duration.ofSeconds(FIVE_MINUTES))
                .map(Boolean.TRUE::equals);
    }
}
