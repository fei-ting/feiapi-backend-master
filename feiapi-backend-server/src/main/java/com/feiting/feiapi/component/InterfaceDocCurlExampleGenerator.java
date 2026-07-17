package com.feiting.feiapi.component;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.model.vo.InterfaceDocParamVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 接口文档 curl 示例生成器。
 *
 * <p>负责构建请求数据、签名脚本、请求 Header 和 Shell 安全转义，不承担数据库访问职责。</p>
 */
@Component
public class InterfaceDocCurlExampleGenerator {

    /** 默认请求内容类型。 */
    private static final String DEFAULT_REQUEST_CONTENT_TYPE = "application/json";

    /** 签名盐值，从配置文件注入。 */
    @Value("${feiapi.client.sign-salt}")
    private String signSalt;

    /**
     * 生成 curl 调用示例。
     *
     * @param interfaceInfo 接口信息
     * @param detailVO      接口文档聚合视图
     * @return curl 调用脚本
     */
    public String generate(InterfaceInfo interfaceInfo, InterfaceDocDetailVO detailVO) {
        if (interfaceInfo == null || detailVO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口文档信息不能为空");
        }
        String method = firstText(interfaceInfo.getMethod(), "GET").toUpperCase(Locale.ROOT);
        String path = buildSignPath(interfaceInfo);
        String gatewayUrl = appendQueryParamsIfNecessary(
                detailVO.getGatewayUrl(), method, safeList(detailVO.getRequestParams()));
        assertNoShellControlCharacter(path, "接口路径不能包含控制字符");
        assertNoShellControlCharacter(gatewayUrl, "接口地址不能包含控制字符");
        String body = "GET".equals(method) ? "" : buildBodyJson(safeList(detailVO.getRequestParams()));
        List<String> curlOptions = buildCurlOptions(detailVO, body);
        String curlCommand = Stream.concat(Stream.of("curl -X \"$METHOD\" \"$URL\""), curlOptions.stream())
                .collect(Collectors.joining(" \\\n"));
        return Stream.of(
                        "#!/usr/bin/env bash",
                        "set -euo pipefail",
                        "",
                        ": \"${ACCESS_KEY:?请先 export ACCESS_KEY='你的 AccessKey'}\"",
                        ": \"${SECRET_KEY:?请先 export SECRET_KEY='你的 SecretKey'}\"",
                        "",
                        "METHOD=" + shellSingleQuote(method),
                        "PATH_VALUE=" + shellSingleQuote(path),
                        "URL=" + shellSingleQuote(gatewayUrl),
                        "BODY=" + shellSingleQuote(body),
                        "NONCE=\"${NONCE:-$(openssl rand -hex 16)}\"",
                        "TIMESTAMP=\"${TIMESTAMP:-$(date +%s)}\"",
                        "SIGN=\"$(",
                        "  printf " + shellSingleQuote(signSalt + "\\n%s\\n%s\\n%s\\n%s\\n%s") + " \\",
                        "    \"$METHOD\" \"$PATH_VALUE\" \"$NONCE\" \"$TIMESTAMP\" \"$BODY\" |",
                        "  openssl dgst -sha256 -hmac \"$SECRET_KEY\" |",
                        "  awk '{print $2}'",
                        ")\"",
                        "",
                        curlCommand)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建固定向量调试使用的规范签名原文。
     *
     * <p>注意：此方法为 public 是为了单元测试验证签名一致性，
     * 生产代码中不应直接调用，应通过 {@link #generate} 获取完整 curl 脚本。</p>
     *
     * @param method    请求方法
     * @param path      签名路径
     * @param nonce     随机数
     * @param timestamp 时间戳
     * @param body      请求正文
     * @return 规范签名原文
     */
    public String buildCanonicalString(String method, String path, String nonce, String timestamp, String body) {
        return signSalt + "\n"
                + nullToEmpty(method) + "\n"
                + nullToEmpty(path) + "\n"
                + nullToEmpty(nonce) + "\n"
                + nullToEmpty(timestamp) + "\n"
                + nullToEmpty(body);
    }

    /**
     * 构建 curl 参数列表。
     *
     * @param detailVO 文档聚合视图
     * @param body     请求正文
     * @return curl 参数列表
     */
    private List<String> buildCurlOptions(InterfaceDocDetailVO detailVO, String body) {
        String contentType = detailVO.getDoc() == null
                ? DEFAULT_REQUEST_CONTENT_TYPE
                : firstText(detailVO.getDoc().getRequestContentType(), DEFAULT_REQUEST_CONTENT_TYPE);
        assertNoShellControlCharacter(contentType, "请求内容类型不能包含控制字符");
        List<String> authHeaders = Stream.of(
                        "  -H \"accessKey: ${ACCESS_KEY}\"",
                        "  -H \"nonce: ${NONCE}\"",
                        "  -H \"timestamp: ${TIMESTAMP}\"",
                        "  -H \"sign: ${SIGN}\"",
                        "  -H " + shellSingleQuote("Content-Type: " + contentType))
                .collect(Collectors.toList());
        List<String> bodyOption = StringUtils.isEmpty(body)
                ? Collections.emptyList()
                : Collections.singletonList("  --data \"$BODY\"");
        return Stream.of(authHeaders, bodyOption)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 为 GET 请求追加 Query 参数。
     *
     * @param url           网关地址
     * @param method        请求方法
     * @param requestParams 请求参数
     * @return 完整请求地址
     */
    private String appendQueryParamsIfNecessary(String url, String method, List<InterfaceDocParamVO> requestParams) {
        assertNoShellControlCharacter(url, "接口地址不能包含控制字符");
        if (!"GET".equalsIgnoreCase(method)) {
            return nullToEmpty(url);
        }
        String query = requestParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene()))
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .map(param -> encode(param.getName()) + "=" + encode(resolveSampleValue(param)))
                .collect(Collectors.joining("&"));
        if (StringUtils.isBlank(query)) {
            return nullToEmpty(url);
        }
        return nullToEmpty(url) + (url.contains("?") ? "&" : "?") + query;
    }

    /**
     * 构建 JSON 请求正文。
     *
     * @param requestParams 请求参数
     * @return JSON 请求正文
     */
    private String buildBodyJson(List<InterfaceDocParamVO> requestParams) {
        List<InterfaceDocParamVO> bodyParams = requestParams.stream()
                .filter(param -> InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .filter(param -> StringUtils.isNotBlank(param.getName()))
                .sorted(Comparator.comparing(param -> Optional.ofNullable(param.getSortOrder()).orElse(0)))
                .collect(Collectors.toList());
        if (bodyParams.isEmpty()) {
            return "";
        }
        JsonObject bodyJson = new JsonObject();
        // 使用普通 for 循环替代 forEach，避免在 lambda 中修改外部对象
        for (InterfaceDocParamVO param : bodyParams) {
            bodyJson.add(param.getName(), toTypedJsonElement(param));
        }
        return bodyJson.toString();
    }

    /**
     * 将示例值转换为声明的 JSON 类型，非法值使用安全默认值。
     *
     * @param param 参数视图
     * @return JSON 元素
     */
    private JsonElement toTypedJsonElement(InterfaceDocParamVO param) {
        String type = firstText(param.getType(), "string").toLowerCase(Locale.ROOT);
        String rawValue = firstText(param.getExampleValue(), param.getDefaultValue());
        switch (type) {
            case "number":
                return toNumberElement(rawValue);
            case "boolean":
                return new JsonPrimitive("true".equalsIgnoreCase(rawValue) || "1".equals(rawValue));
            case "object":
                return toContainerElement(rawValue, true);
            case "array":
                return toContainerElement(rawValue, false);
            default:
                return new JsonPrimitive(rawValue);
        }
    }

    /**
     * 转换数字示例，非法或缺失值使用零。
     *
     * @param rawValue 原始值
     * @return 数字 JSON 元素
     */
    private JsonElement toNumberElement(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return new JsonPrimitive(0);
        }
        try {
            return new JsonPrimitive(new BigDecimal(rawValue.trim()));
        } catch (NumberFormatException e) {
            return new JsonPrimitive(0);
        }
    }

    /**
     * 转换对象或数组示例，类型错误时使用空容器。
     *
     * @param rawValue 原始值
     * @param object   是否期望对象
     * @return 对象或数组 JSON 元素
     */
    private JsonElement toContainerElement(String rawValue, boolean object) {
        if (StringUtils.isBlank(rawValue)) {
            return object ? new JsonObject() : new JsonArray();
        }
        try {
            JsonElement element = JsonParser.parseString(rawValue);
            if ((object && element.isJsonObject()) || (!object && element.isJsonArray())) {
                return element;
            }
            return object ? new JsonObject() : new JsonArray();
        } catch (JsonSyntaxException e) {
            return object ? new JsonObject() : new JsonArray();
        }
    }

    /**
     * 解析 Query 参数示例值。
     *
     * @param param 参数视图
     * @return 示例文本
     */
    private String resolveSampleValue(InterfaceDocParamVO param) {
        JsonElement element = toTypedJsonElement(param);
        return element.isJsonPrimitive() ? element.getAsJsonPrimitive().getAsString() : element.toString();
    }

    /**
     * 构建参与签名的标准路径。
     *
     * @param interfaceInfo 接口信息
     * @return 标准路径
     */
    private String buildSignPath(InterfaceInfo interfaceInfo) {
        String path = nullToEmpty(interfaceInfo.getPath()).trim();
        if (path.isEmpty()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * 使用 UTF-8 对 Query 名称和值编码。
     *
     * @param value 原始文本
     * @return 编码文本
     */
    private String encode(String value) {
        return URLEncoder.encode(nullToEmpty(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * 使用 POSIX 单引号规则转义静态 Shell 文本。
     *
     * @param value 原始文本
     * @return 单引号包裹后的 Shell 文本
     */
    private String shellSingleQuote(String value) {
        return "'" + nullToEmpty(value).replace("'", "'\"'\"'") + "'";
    }

    /**
     * 拒绝会破坏脚本结构或造成 Header 注入的控制字符。
     *
     * @param value        待检查文本
     * @param errorMessage 错误提示
     */
    private void assertNoShellControlCharacter(String value, String errorMessage) {
        if (value != null && (value.indexOf('\0') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
    }

    /**
     * 返回第一个非空文本。
     *
     * @param values 候选文本
     * @return 第一个非空文本
     */
    private String firstText(String... values) {
        return Stream.of(values).filter(StringUtils::isNotBlank).findFirst().orElse("");
    }

    /**
     * 将空值转换为空字符串。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 将可空列表转换为空安全列表。
     *
     * @param values 原始列表
     * @param <T>    元素类型
     * @return 非空列表
     */
    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
