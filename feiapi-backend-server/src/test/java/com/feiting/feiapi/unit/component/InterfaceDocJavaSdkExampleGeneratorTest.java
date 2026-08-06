package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocJavaSdkExampleGenerator;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocParamVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口文档 Java SDK 示例生成器单元测试。
 */
@DisplayName("InterfaceDocJavaSdkExampleGenerator 单元测试")
class InterfaceDocJavaSdkExampleGeneratorTest {

    /** SDK 方法注册器。 */
    private SdkMethodRegistry sdkMethodRegistry;

    /** 被测 Java SDK 示例生成器。 */
    private InterfaceDocJavaSdkExampleGenerator generator;

    /**
     * 初始化真实 SDK 方法注册表和被测对象。
     */
    @BeforeEach
    void setUp() {
        sdkMethodRegistry = new SdkMethodRegistry();
        sdkMethodRegistry.init();
        generator = new InterfaceDocJavaSdkExampleGenerator(sdkMethodRegistry);
    }

    /**
     * 无参 SDK 方法应直接调用，并且只使用环境变量占位凭据。
     */
    @Test
    @DisplayName("无参 SDK 方法生成环境变量配置和直接调用")
    void shouldGenerateNoArgumentSdkExample() {
        String example = generator.generate(interfaceInfo("getLoveWords"), Collections.emptyList());

        assertThat(example)
                .contains("import com.feiting.feiapiclientsdk.client.FeiApiClient;")
                .contains("System.getenv(\"FEIAPI_ACCESS_KEY\")")
                .contains("System.getenv(\"FEIAPI_SECRET_KEY\")")
                .contains("String result = client.getLoveWords();")
                .doesNotContain("requestParam")
                .doesNotContain("accessKey =")
                .doesNotContain("secretKey =");
    }

    /**
     * 有参 SDK 方法应保持结构化参数类型，并按排序值生成稳定 JSON。
     */
    @Test
    @DisplayName("有参 SDK 方法按文档排序生成结构化 JSON")
    void shouldGenerateTypedRequestJsonInDocumentOrder() {
        List<InterfaceDocParamVO> params = Arrays.asList(
                param("enabled", "boolean", "true", null, 3),
                param("name", "string", "张三", null, 1),
                param("age", "number", null, "18", 2),
                param("metadata", "object", "{\"level\":1}", null, 4),
                param("tags", "array", "[\"A\"]", null, 5));

        String example = generator.generate(interfaceInfo("getUsernameByPost"), params);

        assertThat(example)
                .contains("String requestParam = \"{\\\"name\\\":\\\"张三\\\",\\\"age\\\":18,"
                        + "\\\"enabled\\\":true,\\\"metadata\\\":{\\\"level\\\":1},"
                        + "\\\"tags\\\":[\\\"A\\\"]}\";")
                .contains("String result = client.getUsernameByPost(requestParam);");
    }

    /**
     * 缺少示例和默认值时应按声明类型生成安全占位值。
     */
    @Test
    @DisplayName("缺少文档值时按参数类型生成安全占位值")
    void shouldGenerateSafePlaceholderByType() {
        List<InterfaceDocParamVO> params = Arrays.asList(
                param("text", "string", null, null, 1),
                param("count", "number", null, null, 2),
                param("active", "boolean", null, null, 3),
                param("metadata", "object", null, null, 4),
                param("items", "array", null, null, 5));

        String example = generator.generate(interfaceInfo("generateQrCode"), params);

        assertThat(example)
                .contains("\\\"text\\\":\\\"示例文本\\\"")
                .contains("\\\"count\\\":0")
                .contains("\\\"active\\\":false")
                .contains("\\\"metadata\\\":{}")
                .contains("\\\"items\\\":[]");
    }

    /**
     * 文档字符串应先生成 JSON，再转义为合法 Java 字符串字面量。
     */
    @Test
    @DisplayName("字符串参数正确转义为 Java 字符串字面量")
    void shouldEscapeJsonAsJavaStringLiteral() {
        InterfaceDocParamVO param = param("content", "string", "引号\"、反斜杠\\和换行\n结束", null, 1);

        String example = generator.generate(interfaceInfo("generateQrCode"), List.of(param));

        assertThat(example)
                .contains("\\\\\\\"")
                .contains("\\\\\\\\")
                .contains("\\\\n")
                .doesNotContain("换行\n\"");
    }

    /**
     * 类型与文档示例不匹配时不能生成误导代码。
     */
    @Test
    @DisplayName("拒绝与声明类型不匹配的示例值")
    void shouldRejectInvalidTypedExampleValue() {
        InterfaceDocParamVO param = param("age", "number", "not-number", null, 1);

        assertThatThrownBy(() -> generator.generate(interfaceInfo("getUsernameByPost"), List.of(param)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("age")
                .hasMessageContaining("number");
    }

    /**
     * 未注册 SDK 方法不能生成猜测性调用示例。
     */
    @Test
    @DisplayName("拒绝未注册的 SDK 方法")
    void shouldRejectUnsupportedSdkMethod() {
        assertThatThrownBy(() -> generator.generate(interfaceInfo("unknownMethod"), Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unknownMethod");
    }

    /**
     * 构建测试接口信息。
     *
     * @param sdkMethodName SDK 方法名
     * @return 接口信息
     */
    private InterfaceInfo interfaceInfo(String sdkMethodName) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setSdkMethodName(sdkMethodName);
        return interfaceInfo;
    }

    /**
     * 构建测试请求参数。
     *
     * @param name         参数名称
     * @param type         参数类型
     * @param exampleValue 示例值
     * @param defaultValue 默认值
     * @param sortOrder    排序值
     * @return 请求参数
     */
    private InterfaceDocParamVO param(String name,
                                      String type,
                                      String exampleValue,
                                      String defaultValue,
                                      int sortOrder) {
        InterfaceDocParamVO param = new InterfaceDocParamVO();
        param.setParamScene("BODY");
        param.setName(name);
        param.setType(type);
        param.setExampleValue(exampleValue);
        param.setDefaultValue(defaultValue);
        param.setSortOrder(sortOrder);
        return param;
    }
}
