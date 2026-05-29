package com.feiting.feiapiclientsdk.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapiclientsdk.model.User;
import com.feiting.feiapiclientsdk.utils.SignUtils;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 调用第三方接口的客户端
 */
public class FeiApiClient {

    private static final String DEFAULT_GATEWAY_HOST = "http://localhost:8090";

    private String accessKey;
    private String secretKey;
    private String gatewayHost = DEFAULT_GATEWAY_HOST;

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

    /**
     * 随机获取土味情话
     *
     * GET 请求没有请求体，因此签名时传入 null。
     */
    @SdkInvoke(needParams = false)
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
    @SdkInvoke(needParams = true)
    public String getUsernameByPost(String requestParam) {
        Gson gson = new Gson();
        User user = gson.fromJson(requestParam, User.class);
        String json = JSONUtil.toJsonStr(user);

        return executeRequest(HttpRequest.post(gatewayHost + "/api/name/user")
                .addHeaders(getHeaderMap("POST", "/api/name/user", json))
                .body(json));
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
        return headers;
    }

    /**
     * 下游接口返回非 2xx 时，直接抛出异常，避免把失败结果继续包装成成功响应。
     */
    private String executeRequest(HttpRequest request) {
        HttpResponse httpResponse = request.execute();
        int status = httpResponse.getStatus();
        String body = httpResponse.body();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("调用接口失败，响应状态码：" + status);
        }
        return body;
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
