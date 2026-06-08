package com.feiting.feiapigateway;

import com.feiting.feiapiclientsdk.utils.SignUtils;
import com.feiting.feiapiclientsdk.utils.ProbeSignUtils;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.service.InnerInterfaceInfoService;
import com.feiting.feiapicommon.service.InnerUserInterfaceInfoService;
import com.feiting.feiapicommon.service.InnerUserService;
import com.feiting.feiapigateway.config.FeiapiGatewayProperties;
import com.feiting.feiapigateway.utils.GatewayRequestUtils;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网关全局过滤器，负责处理所有经过网关的请求。
 *
 * <p>主要职责：</p>
 * <ol>
 *   <li>用户鉴权：通过 accessKey 查询调用方身份</li>
 *   <li>防重放攻击：校验 nonce 唯一性和 timestamp 时间窗口</li>
 *   <li>签名验证：校验请求完整性，防止参数篡改</li>
 *   <li>限流保护：基于滑动窗口的接口调用频率限制</li>
 *   <li>接口状态路由：区分普通调用和发布探测调用</li>
 *   <li>调用计数：统计用户调用次数并扣减剩余次数</li>
 * </ol>
 */
@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    // region 常量定义

    /** 请求时间戳允许的最大偏差（秒），超过此范围的请求将被拒绝 */
    private static final long FIVE_MINUTES = 60 * 5L;

    /** nonce 固定长度，必须是 32 位字母数字字符串 */
    private static final int NONCE_LENGTH = 32;

    /** 普通调用 nonce 的 Redis key 前缀，格式：feiapi:nonce:{accessKey}:{nonce} */
    private static final String NONCE_KEY_PREFIX = "feiapi:nonce:";

    /** 发布探测 nonce 的 Redis key 前缀，格式：feiapi:probe:nonce:{nonce} */
    private static final String PROBE_NONCE_KEY_PREFIX = "feiapi:probe:nonce:";

    /** 发布探测标记 Header，值为 "true" 表示这是一个发布探测请求 */
    private static final String PROBE_HEADER = "X-FeiAPI-Probe";

    /** 发布探测 nonce Header，用于探测请求的防重放校验 */
    private static final String PROBE_NONCE_HEADER = "X-FeiAPI-Probe-Nonce";

    /** 发布探测时间戳 Header，用于探测请求的时间窗口校验 */
    private static final String PROBE_TIMESTAMP_HEADER = "X-FeiAPI-Probe-Timestamp";

    /** 发布探测签名 Header，使用 probeSecret 计算的 HMAC-SHA256 签名 */
    private static final String PROBE_SIGN_HEADER = "X-FeiAPI-Probe-Sign";

    /** 限流 Lua 脚本，保证计数和过期时间设置的原子性 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();

    // endregion

    static {
        // 使用 Redis Lua 脚本保证计数和过期时间设置的原子性，避免并发请求突破限流窗口。
        RATE_LIMIT_SCRIPT.setScriptText(
                "local current = redis.call('GET', KEYS[1])\n" +
                "if current and tonumber(current) >= tonumber(ARGV[1]) then\n" +
                "  return 0\n" +
                "end\n" +
                "current = redis.call('INCR', KEYS[1])\n" +
                "if tonumber(current) == 1 then\n" +
                "  redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
                "end\n" +
                "return 1");
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

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

    /**
     * 网关核心过滤方法，处理所有经过网关的请求。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>提取请求基础信息（路径、方法、Header）</li>
     *   <li>用户鉴权：通过 accessKey 查询调用方</li>
     *   <li>nonce 校验：防重放攻击</li>
     *   <li>timestamp 校验：时间窗口校验</li>
     *   <li>签名验证：校验请求完整性</li>
     *   <li>nonce 消费：标记 nonce 已使用</li>
     *   <li>接口路由：根据接口状态分发到不同处理链路</li>
     * </ol>
     *
     * @param exchange 请求上下文
     * @param chain    过滤器链
     * @return 处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestPath = request.getPath().value();
        String path = feiapiGatewayProperties.getNormalizedInterfaceHost() + requestPath;
        String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod().toString();
        ServerHttpResponse response = exchange.getResponse();

        // 记录请求基础信息，查询参数会先脱敏，避免敏感字段直接进入日志。
        log.info("请求唯一标识: {}", request.getId());
        log.info("请求路径: {}", requestPath);
        log.info("请求方法: {}", method);
        log.info("请求参数: {}", LogDesensitizeUtils.toSafeQueryParams(request.getQueryParams()));
        log.info("请求来源地址: {}", GatewayRequestUtils.resolveClientIp(request));

        // 提取普通调用签名字段：accessKey 定位调用方，nonce/timestamp 防重放，sign 校验请求完整性。
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String sign = headers.getFirst("sign");
        String timestamp = headers.getFirst("timestamp");

        // 步骤1：根据 accessKey 查询调用用户，后续验签必须使用该用户自己的 secretKey。
        User invokeUser = null;
        try {
            invokeUser = innerUserService.getInvokeUser(accessKey);
        } catch (Exception e) {
            log.error("getInvokeUser error: {}", e.getMessage(), e);
        }
        if (invokeUser == null) {
            return handleNoAuth(response);
        }

        // 步骤2：nonce 必须是固定长度的字母数字串，避免空值、异常长度或特殊字符进入防重放缓存。
        if (!isValidNonce(nonce)) {
            return handleNoAuth(response);
        }

        // 步骤3：timestamp 只允许 5 分钟时间窗口内的请求，降低签名被截获后的重放风险。
        Long requestTimestamp = parseTimestamp(timestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        if (requestTimestamp == null || Math.abs(currentTime - requestTimestamp) > FIVE_MINUTES) {
            return handleNoAuth(response);
        }

        // 步骤4：Gateway 的请求体只能消费一次，这里先聚合 body 用于验签，后面再通过装饰器写回下游请求。
        User finalInvokeUser = invokeUser;
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(response.bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);

                    // 步骤5：签名原文绑定 method、path、nonce、timestamp 和真实 body，任一字段被篡改都会验签失败。
                    String secretKey = finalInvokeUser.getSecretKey();
                    String serverSign = SignUtils.getSign(secretKey, method, requestPath, nonce, timestamp, body);
                    if (sign == null || !sign.equals(serverSign)) {
                        return handleNoAuth(response);
                    }

                    // 步骤6：Redis setIfAbsent 消费 nonce，同一个 accessKey 下的同一 nonce 只能成功使用一次。
                    return tryConsumeNonce(accessKey, nonce)
                            .flatMap(consumed -> {
                                if (!consumed) {
                                    return handleNoAuth(response);
                                }

                                // 步骤7：将已读取过的 body 重新包装回请求，否则下游接口将读不到请求体。
                                DataBufferFactory bufferFactory = response.bufferFactory();
                                ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(request) {
                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return Flux.defer(() -> Flux.just(bufferFactory.wrap(bodyBytes)));
                                    }
                                };
                                ServerWebExchange decoratedExchange = exchange.mutate().request(decoratedRequest).build();

                                // 步骤8：普通调用只走已上线接口链路
                                InterfaceInfo interfaceInfo;
                                try {
                                    interfaceInfo = innerInterfaceInfoService.getInterfaceInfo(path, method);
                                } catch (Exception e) {
                                    log.error("getInterfaceInfo error: {}", e.getMessage(), e);
                                    return handleInvokeError(response);
                                }

                                // 命中上线接口：先限流，再校验剩余调用次数，最后统计成功响应。
                                if (interfaceInfo != null && isOnlineInterface(interfaceInfo)) {
                                    return tryConsumeAccessKeyRateLimit(accessKey, method, requestPath)
                                            .flatMap(rateAllowed -> {
                                                if (!rateAllowed) {
                                                    return handleTooManyRequests(response);
                                                }
                                                return invokeOnlineInterface(decoratedExchange, chain, response, finalInvokeUser, interfaceInfo);
                                            });
                                }

                                // 步骤9：未命中上线接口时，检查是否携带发布探测标记。
                                // 未携带探测标记的普通请求直接返回 404，避免泄露接口存在信息。
                                if (!hasPublishingProbeHeader(request)) {
                                    return handleNotFound(response);
                                }

                                // 步骤10：携带探测标记的请求，需要验证内部探测签名合法性。
                                return validatePublishingProbe(request, method, requestPath)
                                        .flatMap(valid -> {
                                            if (!valid) {
                                                // 探测签名不合法，返回 403 表示鉴权失败。
                                                return handleNoAuth(response);
                                            }

                                            // 步骤11：探测签名验证通过，查询发布验证中的接口。
                                            InterfaceInfo publishingInterfaceInfo;
                                            try {
                                                publishingInterfaceInfo = innerInterfaceInfoService.getPublishingInterfaceInfo(path, method);
                                            } catch (Exception e) {
                                                log.error("getPublishingInterfaceInfo error: {}", e.getMessage(), e);
                                                return handleInvokeError(response);
                                            }
                                            if (publishingInterfaceInfo == null || !isPublishingInterface(publishingInterfaceInfo)) {
                                                // 接口不存在或已不处于发布验证状态，返回 404。
                                                return handleNotFound(response);
                                            }

                                            // 步骤12：发布探测只验证接口可调用性，不扣减用户次数，也不记录普通调用统计。
                                            return chain.filter(decoratedExchange);
                                        });
                            })
                            .onErrorResume(e -> {
                                log.error("tryConsumeNonce error: {}", e.getMessage(), e);
                                return handleInvokeError(response);
                            });
                });
    }

    /**
     * 过滤器优先级，值越小优先级越高。-1 确保在大多数内置过滤器之前执行。
     */
    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * 处理上线接口的响应，统计调用次数。
     *
     * <p>只对 HTTP 200 的下游响应统计调用次数，避免失败请求消耗用户额度。</p>
     * <p>响应状态码需要在下游响应写出后读取，避免过滤器链执行前状态码尚未生成。</p>
     *
     * @param exchange        请求上下文
     * @param chain           过滤器链
     * @param userId          调用用户 ID
     * @param interfaceInfoId 接口 ID
     * @return 处理结果
     */
    public Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain, long userId, long interfaceInfoId) {
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            AtomicBoolean invoked = new AtomicBoolean(false);
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    return super.writeWith(body)
                            .doOnSuccess(unused -> countSuccessfulInvokeOnce(invoked, getStatusCode(), userId, interfaceInfoId));
                }

                @Override
                public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                    return super.writeAndFlushWith(body)
                            .doOnSuccess(unused -> countSuccessfulInvokeOnce(invoked, getStatusCode(), userId, interfaceInfoId));
                }

                @Override
                public Mono<Void> setComplete() {
                    return super.setComplete()
                            .doOnSuccess(unused -> countSuccessfulInvokeOnce(invoked, getStatusCode(), userId, interfaceInfoId));
                }
            };
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        } catch (Exception e) {
            log.error("网关处理响应异常 {}", e.getMessage(), e);
            return chain.filter(exchange);
        }
    }

    /**
     * 在响应成功写出后按请求维度统计一次调用。
     *
     * <p>响应状态码只有在下游处理后才可靠，因此不能在执行过滤器链之前读取。</p>
     *
     * @param invoked         本次请求是否已经计数
     * @param statusCode      下游响应状态码
     * @param userId          调用用户 ID
     * @param interfaceInfoId 接口 ID
     */
    private void countSuccessfulInvokeOnce(AtomicBoolean invoked,
                                           HttpStatusCode statusCode,
                                           long userId,
                                           long interfaceInfoId) {
        if (!HttpStatus.OK.equals(statusCode) || !invoked.compareAndSet(false, true)) {
            return;
        }
        try {
            innerUserInterfaceInfoService.invokeCount(userId, interfaceInfoId);
        } catch (Exception e) {
            log.error("invokeCount error: {}", e.getMessage(), e);
        }
        log.info("接口响应完成, status: {}, userId: {}, interfaceInfoId: {}",
                statusCode.value(), userId, interfaceInfoId);
    }

    /**
     * 返回 403 响应，表示鉴权失败（签名、重放或内部探测校验未通过）。
     */
    private Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    /**
     * 返回 500 响应，表示接口调用失败（接口不存在、已下线或验证失败）。
     */
    private Mono<Void> handleInvokeError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return response.setComplete();
    }

    /**
     * 返回 404 响应，表示接口不存在或已下线。
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>普通请求未命中上线接口</li>
     *   <li>发布探测请求未命中发布验证中的接口</li>
     * </ul>
     */
    private Mono<Void> handleNotFound(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return response.setComplete();
    }

    /**
     * 返回 429 响应，表示当前调用方在窗口期内已经触发限流。
     */
    private Mono<Void> handleTooManyRequests(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return response.setComplete();
    }

    /**
     * 校验 nonce 格式是否合法。
     *
     * <p>nonce 必须是固定长度（32位）的字母数字字符串，避免特殊字符参与缓存键拼接。</p>
     *
     * @param nonce 待校验的 nonce
     * @return 格式是否合法
     */
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

    /**
     * 解析时间戳字符串。
     *
     * <p>解析失败时返回 null，由上层统一拒绝请求。</p>
     *
     * @param timestamp 时间戳字符串
     * @return 解析后的时间戳，解析失败返回 null
     */
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

    /**
     * 判断接口是否为上线状态。
     *
     * @param interfaceInfo 接口信息
     * @return 是否上线
     */
    private boolean isOnlineInterface(InterfaceInfo interfaceInfo) {
        return Integer.valueOf(InterfaceInfoStatusEnum.ONLINE.getValue()).equals(interfaceInfo.getStatus());
    }

    /**
     * 判断接口是否为发布验证中状态。
     *
     * @param interfaceInfo 接口信息
     * @return 是否发布验证中
     */
    private boolean isPublishingInterface(InterfaceInfo interfaceInfo) {
        return Integer.valueOf(InterfaceInfoStatusEnum.PUBLISHING.getValue()).equals(interfaceInfo.getStatus());
    }

    /**
     * 检查请求是否携带发布探测标记 Header。
     *
     * <p>只有显式携带 X-FeiAPI-Probe: true 的请求才会进入发布探测链路，
     * 普通用户请求不会进入此分支。</p>
     *
     * @param request 请求对象
     * @return 是否携带探测标记
     */
    private boolean hasPublishingProbeHeader(ServerHttpRequest request) {
        return "true".equalsIgnoreCase(request.getHeaders().getFirst(PROBE_HEADER));
    }

    /**
     * 处理上线接口的调用。
     *
     * <p>普通上线接口需要先校验用户剩余调用次数，避免透支调用。</p>
     *
     * @param exchange        请求上下文
     * @param chain           过滤器链
     * @param response        响应对象
     * @param invokeUser      调用用户
     * @param interfaceInfo   接口信息
     * @return 处理结果
     */
    private Mono<Void> invokeOnlineInterface(ServerWebExchange exchange,
                                             GatewayFilterChain chain,
                                             ServerHttpResponse response,
                                             User invokeUser,
                                             InterfaceInfo interfaceInfo) {
        Long userId = invokeUser.getId();
        Long interfaceInfoId = interfaceInfo.getId();
        try {
            innerUserInterfaceInfoService.leftNumIsEnough(userId, interfaceInfoId);
        } catch (Exception e) {
            log.error("leftNumIsEnough error: {}", e.getMessage(), e);
            return handleInvokeError(response);
        }
        return handleResponse(exchange, chain, userId, interfaceInfoId);
    }

    /**
     * 校验发布探测请求的合法性。
     *
     * <p>发布探测只在显式携带内部标记时放行，普通用户请求不会进入发布中接口链路。</p>
     * <p>校验步骤：</p>
     * <ol>
     *   <li>检查是否携带探测标记 Header</li>
     *   <li>检查探测密钥是否已配置</li>
     *   <li>校验探测 nonce 格式</li>
     *   <li>校验探测 timestamp 时间窗口</li>
     *   <li>校验探测签名</li>
     *   <li>消费探测 nonce（防重放）</li>
     * </ol>
     *
     * @param request     请求对象
     * @param method      请求方法
     * @param requestPath 请求路径
     * @return 探测请求是否合法
     */
    private Mono<Boolean> validatePublishingProbe(ServerHttpRequest request, String method, String requestPath) {
        HttpHeaders headers = request.getHeaders();

        // 检查是否携带探测标记，未携带则直接拒绝。
        if (!"true".equalsIgnoreCase(headers.getFirst(PROBE_HEADER))) {
            return Mono.just(Boolean.FALSE);
        }

        // 探测密钥只从网关配置读取，未配置时拒绝所有发布探测请求。
        String probeSecret = feiapiGatewayProperties.getProbeSecret();
        if (probeSecret == null || probeSecret.trim().isEmpty()) {
            return Mono.just(Boolean.FALSE);
        }

        // 探测请求使用独立 nonce 和 timestamp，避免与普通调用签名的防重放空间混用。
        String probeNonce = headers.getFirst(PROBE_NONCE_HEADER);
        if (!isValidNonce(probeNonce)) {
            return Mono.just(Boolean.FALSE);
        }

        // 校验探测时间戳，只允许 5 分钟窗口内的请求。
        String probeTimestamp = headers.getFirst(PROBE_TIMESTAMP_HEADER);
        Long parsedProbeTimestamp = parseTimestamp(probeTimestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        if (parsedProbeTimestamp == null || Math.abs(currentTime - parsedProbeTimestamp) > FIVE_MINUTES) {
            return Mono.just(Boolean.FALSE);
        }

        // 内部探测签名绑定 method/path/nonce/timestamp，确保只能探测本次目标接口。
        String probeSign = headers.getFirst(PROBE_SIGN_HEADER);
        String serverProbeSign = ProbeSignUtils.getSign(probeSecret, method, requestPath, probeNonce, probeTimestamp);
        if (probeSign == null || !probeSign.equals(serverProbeSign)) {
            return Mono.just(Boolean.FALSE);
        }

        // 消费探测 nonce，防止重放攻击。
        return tryConsumeProbeNonce(probeNonce);
    }

    /**
     * 消费普通调用的 nonce。
     *
     * <p>普通请求 nonce 按 accessKey 隔离，避免不同调用方之间的 nonce 互相影响。</p>
     *
     * @param accessKey 调用方标识
     * @param nonce     随机数
     * @return 是否消费成功（首次使用返回 true）
     */
    private Mono<Boolean> tryConsumeNonce(String accessKey, String nonce) {
        String nonceKey = NONCE_KEY_PREFIX + accessKey + ":" + nonce;
        return reactiveStringRedisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", Duration.ofSeconds(FIVE_MINUTES))
                .map(Boolean.TRUE::equals);
    }

    /**
     * 消费发布探测的 nonce。
     *
     * <p>发布探测 nonce 使用独立前缀，防止内部探测和普通调用共享防重放键空间。</p>
     *
     * @param probeNonce 探测随机数
     * @return 是否消费成功（首次使用返回 true）
     */
    private Mono<Boolean> tryConsumeProbeNonce(String probeNonce) {
        String nonceKey = PROBE_NONCE_KEY_PREFIX + probeNonce;
        return reactiveStringRedisTemplate.opsForValue()
                .setIfAbsent(nonceKey, "1", Duration.ofSeconds(FIVE_MINUTES))
                .map(Boolean.TRUE::equals);
    }

    /**
     * 尝试消费限流配额。
     *
     * <p>限流维度为 accessKey + method + path，避免单个调用方对单个接口突发打满网关。</p>
     *
     * @param accessKey    调用方标识
     * @param method       请求方法
     * @param requestPath  请求路径
     * @return 是否允许通过（未超限返回 true）
     */
    private Mono<Boolean> tryConsumeAccessKeyRateLimit(String accessKey, String method, String requestPath) {
        String rateLimitKey = GatewayRequestUtils.buildRateLimitKey(accessKey, method, requestPath);
        int maxRequests = feiapiGatewayProperties.getRateLimit().getMaxRequests();
        int windowSeconds = feiapiGatewayProperties.getRateLimit().getWindowSeconds();
        return reactiveStringRedisTemplate.execute(
                        RATE_LIMIT_SCRIPT,
                        Collections.singletonList(rateLimitKey),
                        Arrays.asList(String.valueOf(maxRequests), String.valueOf(windowSeconds)))
                .next()
                .map(result -> result != null && result > 0)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
