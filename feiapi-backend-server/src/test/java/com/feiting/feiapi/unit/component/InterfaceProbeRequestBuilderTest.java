package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.interfaceplatform.publishing.component.InterfaceProbeRequestBuilder;
import com.feiting.feiapi.interfaceplatform.publishing.exception.InterfacePublishProbeException;
import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocParamSnapshot;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口发布探测请求参数构造器单元测试。
 */
@DisplayName("接口发布探测请求参数构造器单元测试")
class InterfaceProbeRequestBuilderTest {

    /**
     * 被测构造器。
     */
    private final InterfaceProbeRequestBuilder builder = new InterfaceProbeRequestBuilder();

    /**
     * 应优先使用结构化文档示例值，缺失时使用运行时模板值。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("按示例值优先级构造探测请求")
    void shouldBuildProbeRequestByExampleValuePriority() throws Exception {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setRequestParams("{\"name\":\"模板名称\",\"age\":18}");
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceInfo(interfaceInfo);
        context.setSdkMethod(resolveSdkMethod("getUsernameByPost", String.class));
        context.setDocParams(List.of(
                buildRequestParam("name", "string", "文档名称"),
                buildRequestParam("age", "number", null)
        ));

        String requestJson = builder.build(context);

        JsonObject requestObject = JsonParser.parseString(requestJson).getAsJsonObject();
        assertThat(requestObject.get("name").getAsString()).isEqualTo("文档名称");
        assertThat(requestObject.get("age").getAsInt()).isEqualTo(18);
    }

    /**
     * 字符串类型标记不能作为真实探测值。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("字符串类型标记缺少安全探测值")
    void shouldRejectStringTypeMarkerWhenExampleValueMissing() throws Exception {
        InterfacePublishContext context = buildPostContext("{\"name\":\"string\"}",
                List.of(buildRequestParam("name", "string", null)));

        assertThatThrownBy(() -> builder.build(context))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.SDK_INVOCATION);
                    assertThat(exception.getMessage()).contains("缺少可安全探测的参数值：name");
                });
    }

    /**
     * JSON null 不能被字符串参数静默转换为空字符串探测值。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("JSON null 缺少安全探测值")
    void shouldRejectNullStringTemplateValueWhenExampleValueMissing() throws Exception {
        InterfacePublishContext context = buildPostContext("{\"name\":null}",
                List.of(buildRequestParam("name", "string", null)));

        assertThatThrownBy(() -> builder.build(context))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.SDK_INVOCATION);
                    assertThat(exception.getMessage()).contains("缺少可安全探测的参数值：name");
                });
    }

    /**
     * 结构化文档显式示例值应优先于模板类型标记。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("结构化文档显式示例值允许为 string")
    void shouldUseExplicitDocExampleValueEvenWhenTemplateIsStringMarker() throws Exception {
        InterfacePublishContext context = buildPostContext("{\"name\":\"string\"}",
                List.of(buildRequestParam("name", "string", "string")));

        String requestJson = builder.build(context);

        JsonObject requestObject = JsonParser.parseString(requestJson).getAsJsonObject();
        assertThat(requestObject.get("name").getAsString()).isEqualTo("string");
    }

    /**
     * 数字、布尔、对象和数组示例值必须按声明类型写入探测 JSON。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("按声明类型标准化结构化探测值")
    void shouldNormalizeProbeValuesByDeclaredType() throws Exception {
        InterfacePublishContext context = buildPostContext(
                "{\"count\":1,\"enabled\":false,\"profile\":{},\"tags\":[]}",
                List.of(
                        buildRequestParam("count", "number", "2.5"),
                        buildRequestParam("enabled", "boolean", "true"),
                        buildRequestParam("profile", "object", "{\"name\":\"模拟用户\"}"),
                        buildRequestParam("tags", "array", "[\"demo\"]")));

        JsonObject requestObject = JsonParser.parseString(builder.build(context)).getAsJsonObject();

        assertThat(requestObject.get("count").getAsBigDecimal()).isEqualByComparingTo("2.5");
        assertThat(requestObject.get("enabled").getAsBoolean()).isTrue();
        assertThat(requestObject.getAsJsonObject("profile").get("name").getAsString()).isEqualTo("模拟用户");
        assertThat(requestObject.getAsJsonArray("tags").get(0).getAsString()).isEqualTo("demo");
    }

    /**
     * 非 true/false 的布尔示例值不能用于真实探测。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("拒绝非法布尔探测值")
    void shouldRejectInvalidBooleanProbeValue() throws Exception {
        InterfacePublishContext context = buildPostContext("{\"enabled\":false}",
                List.of(buildRequestParam("enabled", "boolean", "yes")));

        assertThatThrownBy(() -> builder.build(context))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.SDK_INVOCATION);
                    assertThat(exception.getMessage()).contains("请求参数布尔值只允许 true 或 false：enabled");
                });
    }

    /**
     * 运行时参数缺少结构化文档时不能静默构造探测请求。
     *
     * @throws Exception 反射异常
     */
    @Test
    @DisplayName("运行时参数缺少结构化文档时失败")
    void shouldRejectRuntimeParamWithoutStructuredDoc() throws Exception {
        InterfacePublishContext context = buildPostContext("{\"name\":\"模拟用户\"}", List.of());

        assertThatThrownBy(() -> builder.build(context))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.SDK_INVOCATION);
                    assertThat(exception.getMessage()).contains("运行时参数缺少结构化文档：name");
                });
    }

    /**
     * 构造 POST 请求发布上下文。
     *
     * @param requestParams 运行时请求参数模板
     * @param docParams     结构化文档参数
     * @return 发布上下文
     * @throws Exception 反射异常
     */
    private InterfacePublishContext buildPostContext(String requestParams, List<InterfaceDocParamSnapshot> docParams)
            throws Exception {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setRequestParams(requestParams);
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceInfo(interfaceInfo);
        context.setSdkMethod(resolveSdkMethod("getUsernameByPost", String.class));
        context.setDocParams(docParams);
        return context;
    }

    /**
     * 解析 SDK 方法。
     *
     * @param name           方法名称
     * @param parameterTypes 参数类型
     * @return SDK 方法
     * @throws Exception 反射异常
     */
    private Method resolveSdkMethod(String name, Class<?>... parameterTypes) throws Exception {
        return FeiApiClient.class.getDeclaredMethod(name, parameterTypes);
    }

    /**
     * 构造请求参数文档。
     *
     * @param name         参数名称
     * @param type         参数类型
     * @param exampleValue 示例值
     * @return 文档参数
     */
    private InterfaceDocParamSnapshot buildRequestParam(String name, String type, String exampleValue) {
        return InterfaceDocParamSnapshot.builder()
                .name(name)
                .type(type)
                .paramScene(InterfaceDocParamSceneEnum.BODY.getValue())
                .exampleValue(exampleValue)
                .build();
    }
}
