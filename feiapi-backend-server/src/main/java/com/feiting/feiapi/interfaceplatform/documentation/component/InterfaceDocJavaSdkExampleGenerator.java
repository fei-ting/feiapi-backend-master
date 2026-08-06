package com.feiting.feiapi.interfaceplatform.documentation.component;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocParamVO;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 接口文档 Java SDK 示例生成器。
 *
 * <p>根据真实 SDK 注册方法和结构化请求参数生成公开示例，不读取或接收任何用户凭据。</p>
 */
@Component
public class InterfaceDocJavaSdkExampleGenerator {

    /** 缺少字符串示例时使用的公开占位值。 */
    private static final String DEFAULT_STRING_VALUE = "示例文本";

    /** SDK 方法注册器。 */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 创建 Java SDK 示例生成器。
     *
     * @param sdkMethodRegistry SDK 方法注册器
     */
    public InterfaceDocJavaSdkExampleGenerator(SdkMethodRegistry sdkMethodRegistry) {
        this.sdkMethodRegistry = sdkMethodRegistry;
    }

    /**
     * 生成 Java SDK 调用示例。
     *
     * @param interfaceInfo 接口信息
     * @param requestParams 结构化请求参数
     * @return Java SDK 调用示例
     */
    public String generate(InterfaceInfo interfaceInfo, List<InterfaceDocParamVO> requestParams) {
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口信息不能为空");
        }
        String sdkMethodName = StringUtils.trimToNull(interfaceInfo.getSdkMethodName());
        if (sdkMethodName == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SDK 方法名不能为空");
        }
        Method sdkMethod = sdkMethodRegistry.getMethodMap().get(sdkMethodName);
        if (sdkMethod == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的接口方法：" + sdkMethodName);
        }
        SdkInvoke sdkInvoke = sdkMethod.getAnnotation(SdkInvoke.class);
        if (sdkInvoke == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "SDK 方法缺少调用契约：" + sdkMethodName);
        }
        validateMethodContract(sdkMethod, sdkInvoke);
        return sdkInvoke.needParams()
                ? buildParameterizedExample(sdkMethodName, buildRequestJson(requestParams))
                : buildNoArgumentExample(sdkMethodName);
    }

    /**
     * 校验 SDK 方法签名与注解契约一致。
     *
     * @param sdkMethod SDK 反射方法
     * @param sdkInvoke SDK 调用注解
     */
    private void validateMethodContract(Method sdkMethod, SdkInvoke sdkInvoke) {
        boolean validContract = sdkInvoke.needParams()
                ? sdkMethod.getParameterCount() == 1 && String.class.equals(sdkMethod.getParameterTypes()[0])
                : sdkMethod.getParameterCount() == 0;
        if (!validContract || !String.class.equals(sdkMethod.getReturnType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "SDK 方法签名不受支持：" + sdkMethod.getName());
        }
    }

    /**
     * 构建无参 SDK 示例。
     *
     * @param sdkMethodName SDK 方法名
     * @return Java SDK 示例
     */
    private String buildNoArgumentExample(String sdkMethodName) {
        return buildExampleLines(Stream.of(
                "        String result = client." + sdkMethodName + "();",
                "        System.out.println(result);"));
    }

    /**
     * 构建带请求参数的 SDK 示例。
     *
     * @param sdkMethodName SDK 方法名
     * @param requestJson   请求 JSON
     * @return Java SDK 示例
     */
    private String buildParameterizedExample(String sdkMethodName, String requestJson) {
        return buildExampleLines(Stream.of(
                "        String requestParam = \"" + escapeJavaString(requestJson) + "\";",
                "        String result = client." + sdkMethodName + "(requestParam);",
                "        System.out.println(result);"));
    }

    /**
     * 构建完整 Java 示例代码。
     *
     * @param invocationLines 调用代码行
     * @return Java SDK 示例
     */
    private String buildExampleLines(Stream<String> invocationLines) {
        return Stream.concat(
                        Stream.of(
                                "import com.feiting.feiapiclientsdk.client.FeiApiClient;",
                                "",
                                "public class InterfaceExample {",
                                "    public static void main(String[] args) {",
                                "        FeiApiClient client = new FeiApiClient(",
                                "                System.getenv(\"FEIAPI_ACCESS_KEY\"),",
                                "                System.getenv(\"FEIAPI_SECRET_KEY\")",
                                "        );",
                                ""),
                        Stream.concat(invocationLines, Stream.of("    }", "}")))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 根据结构化请求参数构建 JSON。
     *
     * @param requestParams 请求参数
     * @return 请求 JSON
     */
    private String buildRequestJson(List<InterfaceDocParamVO> requestParams) {
        JsonObject requestJson = new JsonObject();
        Optional.ofNullable(requestParams)
                .orElse(Collections.emptyList())
                .stream()
                .filter(java.util.Objects::nonNull)
                .filter(this::isRequestParam)
                .sorted(Comparator.comparing(
                        param -> Optional.ofNullable(param.getSortOrder()).orElse(Integer.MAX_VALUE)))
                .forEach(param -> requestJson.add(requireParamName(param), buildParamValue(param)));
        return requestJson.toString();
    }

    /**
     * 判断参数是否属于请求参数。
     *
     * @param param 文档参数
     * @return 是否为 Query 或 Body 参数
     */
    private boolean isRequestParam(InterfaceDocParamVO param) {
        return InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene());
    }

    /**
     * 获取必填的参数名称。
     *
     * @param param 文档参数
     * @return 参数名称
     */
    private String requireParamName(InterfaceDocParamVO param) {
        String name = StringUtils.trimToNull(param.getName());
        if (name == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数名称不能为空");
        }
        return name;
    }

    /**
     * 根据参数类型构建 JSON 值。
     *
     * @param param 文档参数
     * @return JSON 值
     */
    private JsonElement buildParamValue(InterfaceDocParamVO param) {
        String name = requireParamName(param);
        String type = StringUtils.lowerCase(StringUtils.trimToEmpty(param.getType()), Locale.ROOT);
        String documentValue = firstText(param.getExampleValue(), param.getDefaultValue());
        try {
            return switch (type) {
                case "string" -> new JsonPrimitive(firstText(documentValue, DEFAULT_STRING_VALUE));
                case "number" -> new JsonPrimitive(documentValue == null ? BigDecimal.ZERO : new BigDecimal(documentValue));
                case "boolean" -> buildBooleanValue(name, documentValue);
                case "object" -> buildStructuredValue(name, type, documentValue, true);
                case "array" -> buildStructuredValue(name, type, documentValue, false);
                default -> throw invalidParamValue(name, type, "参数类型不受支持");
            };
        } catch (NumberFormatException | JsonSyntaxException exception) {
            throw invalidParamValue(name, type, "示例值格式错误");
        }
    }

    /**
     * 构建布尔 JSON 值。
     *
     * @param name          参数名称
     * @param documentValue 文档值
     * @return JSON 布尔值
     */
    private JsonElement buildBooleanValue(String name, String documentValue) {
        if (documentValue == null) {
            return new JsonPrimitive(false);
        }
        if (!"true".equalsIgnoreCase(documentValue) && !"false".equalsIgnoreCase(documentValue)) {
            throw invalidParamValue(name, "boolean", "示例值格式错误");
        }
        return new JsonPrimitive(Boolean.parseBoolean(documentValue));
    }

    /**
     * 构建对象或数组 JSON 值。
     *
     * @param name          参数名称
     * @param type          参数类型
     * @param documentValue 文档值
     * @param objectType    是否要求对象类型
     * @return JSON 对象或数组
     */
    private JsonElement buildStructuredValue(String name,
                                             String type,
                                             String documentValue,
                                             boolean objectType) {
        JsonElement value = JsonParser.parseString(documentValue == null
                ? (objectType ? "{}" : "[]")
                : documentValue);
        if ((objectType && !value.isJsonObject()) || (!objectType && !value.isJsonArray())) {
            throw invalidParamValue(name, type, "示例值类型不匹配");
        }
        return value;
    }

    /**
     * 构建参数示例错误。
     *
     * @param name   参数名称
     * @param type   参数类型
     * @param reason 错误原因
     * @return 业务异常
     */
    private BusinessException invalidParamValue(String name, String type, String reason) {
        return new BusinessException(ErrorCode.PARAMS_ERROR,
                "请求参数 " + name + " 的 " + type + " " + reason);
    }

    /**
     * 获取首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本
     */
    private String firstText(String... values) {
        return Stream.of(values)
                .map(StringUtils::trimToNull)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * 将 JSON 转义为 Java 字符串字面量内容。
     *
     * @param value 原始 JSON
     * @return 转义后的 Java 字符串内容
     */
    private String escapeJavaString(String value) {
        StringBuilder escaped = new StringBuilder();
        value.chars().forEach(character -> {
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", character));
                    } else {
                        escaped.append((char) character);
                    }
                }
            }
        });
        return escaped.toString();
    }
}
