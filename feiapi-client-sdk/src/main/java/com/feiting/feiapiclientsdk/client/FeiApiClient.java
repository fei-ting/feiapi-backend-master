package com.feiting.feiapiclientsdk.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapiclientsdk.constant.ApiPayloadLimits;
import com.feiting.feiapiclientsdk.model.OnlineDebugInvocationResult;
import com.feiting.feiapiclientsdk.model.ProbeInvocationResult;
import com.feiting.feiapiclientsdk.model.ProbeStrategy;
import com.feiting.feiapiclientsdk.model.User;
import com.feiting.feiapiclientsdk.utils.ProbeSignUtils;
import com.feiting.feiapiclientsdk.utils.ProbeResponseBodyReader;
import com.feiting.feiapiclientsdk.utils.SignUtils;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 调用第三方接口的客户端
 */
public class FeiApiClient {

    private static final String DEFAULT_GATEWAY_HOST = "http://localhost:8090";

    /**
     * 异常响应体最大展示长度，避免下游返回大文本时污染平台错误信息。
     */
    private static final int MAX_ERROR_BODY_LENGTH = 200;

    /** 发布探测响应体受限读取器。 */
    private final ProbeResponseBodyReader probeResponseBodyReader = new ProbeResponseBodyReader();

    /** 网关受控失败阶段 Header。 */
    private static final String PROBE_FAILURE_STAGE_HEADER = "X-FeiAPI-Probe-Failure-Stage";

    private String accessKey;
    private String secretKey;
    private String gatewayHost = DEFAULT_GATEWAY_HOST;
    private String probeSecret;

    /**
     * 发布探测模式标记。
     *
     * FeiApiClient 作为 Spring 单例 Bean 时会被多个请求线程共享，因此这里使用 ThreadLocal
     * 隔离每个线程的探测状态，避免并发发布时互相污染。调用方必须使用 try/finally 成对调用
     * enableProbeMode() 和 disableProbeMode()，disableProbeMode() 内部使用 remove() 清理状态，
     * 防止线程池复用线程时残留探测模式。
     */
    private final ThreadLocal<Boolean> probeMode = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 当前线程最近一次发布探测响应元数据。
     */
    private final ThreadLocal<ProbeInvocationResult> probeInvocationResult = new ThreadLocal<>();

    /**
     * 在线调试模式标记。
     *
     * <p>在线调试通过临时 SDK 客户端发起调用，但仍使用线程隔离状态，保证未来客户端复用时
     * 不会发生不同请求之间的响应数据串扰。调用方必须使用 try/finally 成对开启和关闭模式。</p>
     */
    private final ThreadLocal<Boolean> onlineDebugMode = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 当前线程最近一次在线调试响应元数据。
     */
    private final ThreadLocal<OnlineDebugInvocationResult> onlineDebugInvocationResult = new ThreadLocal<>();

    public FeiApiClient() {
    }

    public FeiApiClient(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public FeiApiClient(String accessKey, String secretKey, String gatewayHost) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.gatewayHost = normalizeGatewayHost(gatewayHost);
    }

    public FeiApiClient(String accessKey, String secretKey, String gatewayHost, String probeSecret) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.gatewayHost = normalizeGatewayHost(gatewayHost);
        this.probeSecret = probeSecret;
    }

    public void setProbeSecret(String probeSecret) {
        this.probeSecret = probeSecret;
    }

    public void enableProbeMode() {
        this.probeInvocationResult.remove();
        this.probeMode.set(Boolean.TRUE);
    }

    public void disableProbeMode() {
        this.probeMode.remove();
        this.probeInvocationResult.remove();
    }

    /**
     * 获取当前线程最近一次发布探测响应元数据。
     *
     * @return 探测响应元数据
     */
    public ProbeInvocationResult getProbeInvocationResult() {
        return probeInvocationResult.get();
    }

    /**
     * 开启当前线程的在线调试响应捕获模式。
     */
    public void enableOnlineDebugMode() {
        onlineDebugInvocationResult.remove();
        onlineDebugMode.set(Boolean.TRUE);
    }

    /**
     * 关闭当前线程的在线调试响应捕获模式并清理响应元数据。
     */
    public void disableOnlineDebugMode() {
        onlineDebugMode.remove();
        onlineDebugInvocationResult.remove();
    }

    /**
     * 获取当前线程最近一次在线调试响应元数据。
     *
     * @return 在线调试响应元数据；尚未收到 HTTP 响应时返回 null
     */
    public OnlineDebugInvocationResult getOnlineDebugInvocationResult() {
        return onlineDebugInvocationResult.get();
    }

    /**
     * 随机获取土味情话
     *
     * GET 请求没有请求体，因此签名时传入 null。
     */
    @SdkInvoke(needParams = false, probeStrategy = ProbeStrategy.SAFE_REAL_CALL)
    public String getLoveWords() {
        return executeRequest(HttpRequest.get(gatewayHost + "/api/love_words")
                .addHeaders(getHeaderMap("GET", "/api/love_words", null)));
    }

    /**
     * 根据用户对象获取用户名
     *
     * POST 请求的签名必须基于真实发送的请求体计算。
     * 所以这里先把业务对象转成 json，再把这份 json 同时用于：
     * 1. 参与签名
     * 2. 作为真实 HTTP Body 发送给网关
     *
     * 这样 SDK 侧签名时使用的 body，和网关侧验签时读取到的真实 body 才能保持一致。
     *
     * @param requestParam 请求参数
     * @return 响应结果
     */
    @SdkInvoke(needParams = true, probeStrategy = ProbeStrategy.SAFE_REAL_CALL)
    public String getUsernameByPost(String requestParam) {
        Gson gson = new Gson();
        User user = gson.fromJson(requestParam, User.class);
        String json = JSONUtil.toJsonStr(user);

        return executeRequest(HttpRequest.post(gatewayHost + "/api/name/user")
                .addHeaders(getHeaderMap("POST", "/api/name/user", json))
                .body(json));
    }

    /**
     * 生成二维码
     *
     * 根据传入的请求参数生成二维码图片，返回 Base64 编码和 Data URI。
     * 请求参数示例：{"content": "https://example.com", "width": 300, "height": 300}
     *
     * @param requestParam 请求参数 JSON 字符串
     * @return 响应结果（包含 base64 和 dataUri）
     */
    @SdkInvoke(needParams = true, probeStrategy = ProbeStrategy.SAFE_REAL_CALL)
    public String generateQrCode(String requestParam) {
        return executeRequest(HttpRequest.post(gatewayHost + "/api/qrcode/generate")
                .addHeaders(getHeaderMap("POST", "/api/qrcode/generate", requestParam))
                .body(requestParam));
    }

    /**
     * 构造请求头
     * 
     * 整改后，这里不再把 body 放到 header 中传输。
     * 原因是：
     * 1. body 属于真正的请求体，不应伪装成 header 参与协议传输
     * 2. 网关现在会读取真实 HTTP Body 参与验签，不再信任 header 中的 body 字段
     *
     * 这里接收 method / path / body 三个参数，是因为 SDK 签名已经从“固定用户签名”
     * 升级为“针对本次请求的动态签名”：
     * - method 代表本次请求的方法
     * - path 代表本次请求的目标路径
     * - body 代表本次请求真正要发送的请求体
     *
     * nonce 和 timestamp 必须先生成，再参与签名计算，
     * 因为它们本身就是签名原文的一部分，而不是签完名后再额外附带的无关字段。
     *
     * @param method 请求方法
     * @param path 请求路径
     * @param body 请求体
     * @return 请求头
     */
    private Map<String, String> getHeaderMap(String method, String path, String body) {
        assertRequestBodySize(body);
        Map<String, String> headers = new HashMap<>();

        // 为本次请求生成随机数和时间戳。
        // 它们会一起进入签名原文，使得同一用户在不同请求上的签名不再固定。
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

        // 使用“真实将要发送的请求”生成签名。
        // 这样 method、path、nonce、timestamp、body 任意一项被篡改，验签都会失败。
        String sign = SignUtils.getSign(secretKey, method, path, nonce, timestamp, body);

        headers.put("accessKey", accessKey);
        headers.put("nonce", nonce);
        headers.put("sign", sign);
        headers.put("timestamp", timestamp);
        addProbeHeadersIfNecessary(headers, method, path);
        return headers;
    }

    private void addProbeHeadersIfNecessary(Map<String, String> headers, String method, String path) {
        if (!Boolean.TRUE.equals(probeMode.get())) {
            return;
        }
        if (probeSecret == null || probeSecret.trim().isEmpty()) {
            throw new RuntimeException("发布探测密钥不能为空");
        }
        String probeNonce = UUID.randomUUID().toString().replace("-", "");
        String probeTimestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String probeSign = ProbeSignUtils.getSign(probeSecret, method, path, probeNonce, probeTimestamp);
        headers.put("X-FeiAPI-Probe", "true");
        headers.put("X-FeiAPI-Probe-Nonce", probeNonce);
        headers.put("X-FeiAPI-Probe-Timestamp", probeTimestamp);
        headers.put("X-FeiAPI-Probe-Sign", probeSign);
    }

    /**
     * 执行接口请求。
     *
     * <p>普通调用遇到非 2xx 时继续抛出异常，避免把失败结果包装成成功响应。
     * 发布探测调用需要把状态码、响应体和网关失败阶段交给后端统一校验器分类，
     * 在线调试调用需要把真实状态和正文交给页面展示，因此这两种模式都不会在 SDK 内
     * 提前抛出非 2xx 异常。</p>
     *
     * @param request HTTP 请求
     * @return 响应体
     */
    private String executeRequest(HttpRequest request) {
        if (Boolean.TRUE.equals(probeMode.get())) {
            try (HttpResponse httpResponse = request
                    .setConnectionTimeout(3_000)
                    .setReadTimeout(10_000)
                    .executeAsync()) {
                int status = httpResponse.getStatus();
                String body = probeResponseBodyReader.read(httpResponse);
                ProbeInvocationResult result = new ProbeInvocationResult();
                result.setStatusCode(status);
                result.setContentType(httpResponse.header("Content-Type"));
                result.setGatewayFailureStage(httpResponse.header(PROBE_FAILURE_STAGE_HEADER));
                result.setBody(body);
                probeInvocationResult.set(result);
                return body;
            }
        }
        if (Boolean.TRUE.equals(onlineDebugMode.get())) {
            long startNanos = System.nanoTime();
            try (HttpResponse httpResponse = request.executeAsync()) {
                int status = httpResponse.getStatus();
                String body = probeResponseBodyReader.read(httpResponse);
                OnlineDebugInvocationResult result = new OnlineDebugInvocationResult();
                result.setStatusCode(status);
                result.setContentType(httpResponse.header("Content-Type"));
                result.setBody(body);
                result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
                onlineDebugInvocationResult.set(result);
                return body;
            }
        }
        try (HttpResponse httpResponse = request.execute()) {
            int status = httpResponse.getStatus();
            String body = httpResponse.body();
            if (status < 200 || status >= 300) {
                throw new RuntimeException(buildErrorMessage(status, body));
            }
            return body;
        }
    }

    /**
     * 校验最终参与签名并发送的请求体 UTF-8 字节数。
     *
     * @param body 最终请求体
     */
    private void assertRequestBodySize(String body) {
        int bodyBytes = body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
        if (bodyBytes > ApiPayloadLimits.MAX_SIGNED_REQUEST_BODY_BYTES) {
            throw new IllegalArgumentException("请求体不能超过 65535 字节");
        }
    }

    /**
     * 构建下游接口失败提示。
     *
     * @param status 响应状态码
     * @param body   响应体
     * @return 失败提示
     */
    private String buildErrorMessage(int status, String body) {
        StringBuilder messageBuilder = new StringBuilder("调用接口失败，响应状态码：").append(status);
        if (body == null || body.trim().isEmpty()) {
            return messageBuilder.toString();
        }
        String trimmedBody = body.trim();
        if (trimmedBody.length() > MAX_ERROR_BODY_LENGTH) {
            trimmedBody = trimmedBody.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
        }
        return messageBuilder.append("，响应内容：").append(trimmedBody).toString();
    }

    private String normalizeGatewayHost(String gatewayHost) {
        if (gatewayHost == null || gatewayHost.trim().isEmpty()) {
            return DEFAULT_GATEWAY_HOST;
        }
        String trimmedGatewayHost = gatewayHost.trim();
        while (trimmedGatewayHost.endsWith("/")) {
            trimmedGatewayHost = trimmedGatewayHost.substring(0, trimmedGatewayHost.length() - 1);
        }
        return trimmedGatewayHost;
    }
}
