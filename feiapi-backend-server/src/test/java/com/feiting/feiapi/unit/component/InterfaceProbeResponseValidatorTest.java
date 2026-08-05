package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.component.InterfaceProbeResponseValidator;
import com.feiting.feiapi.exception.InterfacePublishProbeException;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapiclientsdk.model.ProbeInvocationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口发布探测响应契约校验器单元测试。
 */
@DisplayName("接口发布探测响应契约校验器单元测试")
class InterfaceProbeResponseValidatorTest {

    /**
     * 被测校验器。
     */
    private final InterfaceProbeResponseValidator validator = new InterfaceProbeResponseValidator();

    /**
     * 非空响应字段缺失时应按响应结构失败分类。
     */
    @Test
    @DisplayName("非空响应字段缺失时失败")
    void shouldFailWhenRequiredResponseFieldMissing() {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(buildJsonDoc());
        context.setDocParams(List.of(buildResponseParam(1L, "name", "string")));
        ProbeInvocationResult result = new ProbeInvocationResult();
        result.setStatusCode(200);
        result.setContentType("application/json;charset=UTF-8");
        result.setBody("{}");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getStage())
                                .isEqualTo(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE));
    }

    /**
     * 网关失败阶段应优先于 HTTP 状态码分类。
     */
    @Test
    @DisplayName("网关失败阶段优先分类")
    void shouldClassifyGatewayFailureStageFirst() {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(buildJsonDoc());
        context.setDocParams(List.of());
        ProbeInvocationResult result = new ProbeInvocationResult();
        result.setStatusCode(403);
        result.setContentType("text/plain;charset=UTF-8");
        result.setGatewayFailureStage("GATEWAY_AUTH");
        result.setBody("probe-denied");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.GATEWAY_AUTH));
    }

    /**
     * 非 2xx 且没有网关失败阶段时，应分类为下游状态失败。
     */
    @Test
    @DisplayName("非 2xx 状态分类为下游状态失败")
    void shouldClassifyDownstreamStatusWhenNoGatewayFailureStage() {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(buildJsonDoc());
        context.setDocParams(List.of());
        ProbeInvocationResult result = new ProbeInvocationResult();
        result.setStatusCode(500);
        result.setContentType("text/plain;charset=UTF-8");
        result.setBody("downstream-error");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.DOWNSTREAM_STATUS));
    }

    /**
     * 无结构化响应字段时，JSON 标量数组应通过响应结构校验。
     */
    @Test
    @DisplayName("无响应字段时允许 JSON 标量数组")
    void shouldAllowScalarJsonArrayWhenNoResponseFieldsConfigured() {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(buildJsonDoc("[1,2,3]"));
        context.setDocParams(List.of());
        ProbeInvocationResult result = new ProbeInvocationResult();
        result.setStatusCode(200);
        result.setContentType("application/json;charset=UTF-8");
        result.setBody("[1,2,3]");

        assertThatCode(() -> validator.validate(context, result)).doesNotThrowAnyException();
    }

    /**
     * 配置结构化响应字段时，JSON 数组元素仍必须是对象。
     */
    @Test
    @DisplayName("有响应字段时拒绝 JSON 标量数组")
    void shouldRejectScalarJsonArrayWhenResponseFieldsConfigured() {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(buildJsonDoc("[{\"name\":\"张三\"}]"));
        context.setDocParams(List.of(buildResponseParam(1L, "name", "string")));
        ProbeInvocationResult result = new ProbeInvocationResult();
        result.setStatusCode(200);
        result.setContentType("application/json;charset=UTF-8");
        result.setBody("[1,2,3]");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE));
    }

    /**
     * 响应媒体类型与文档声明不兼容时应按响应格式失败分类。
     */
    @Test
    @DisplayName("响应媒体类型不兼容时失败")
    void shouldRejectIncompatibleContentType() {
        InterfacePublishContext context = buildContext(buildJsonDoc(), List.of());
        ProbeInvocationResult result = buildResult(200, "text/plain;charset=UTF-8", "{}");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_FORMAT));
    }

    /**
     * 文档声明 JSON 时，非法 JSON 响应不能通过探测。
     */
    @Test
    @DisplayName("非法 JSON 响应按格式失败分类")
    void shouldRejectInvalidJsonResponse() {
        InterfacePublishContext context = buildContext(buildJsonDoc(), List.of());
        ProbeInvocationResult result = buildResult(200, "application/json", "not-json");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_FORMAT));
    }

    /**
     * 实际 JSON 根类型必须与成功示例保持一致。
     */
    @Test
    @DisplayName("JSON 根类型不一致时失败")
    void shouldRejectMismatchedJsonRootType() {
        InterfacePublishContext context = buildContext(buildJsonDoc("{}"), List.of());
        ProbeInvocationResult result = buildResult(200, "application/json", "[]");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_FORMAT));
    }

    /**
     * nullable 字段允许缺失，且不得误判为结构失败。
     */
    @Test
    @DisplayName("可空响应字段允许缺失")
    void shouldAllowMissingNullableResponseField() {
        InterfaceDocParam nullableParam = buildResponseParam(1L, "remark", "string");
        nullableParam.setNullable(1);
        InterfacePublishContext context = buildContext(buildJsonDoc(), List.of(nullableParam));
        ProbeInvocationResult result = buildResult(200, "application/json", "{}");

        assertThatCode(() -> validator.validate(context, result)).doesNotThrowAnyException();
    }

    /**
     * 嵌套响应字段存在但类型错误时必须失败。
     */
    @Test
    @DisplayName("嵌套响应字段类型错误时失败")
    void shouldRejectNestedResponseFieldTypeMismatch() {
        InterfaceDocParam profile = buildResponseParam(1L, "profile", "object");
        InterfaceDocParam age = buildResponseParam(2L, "age", "number");
        age.setParentId(1L);
        InterfacePublishContext context = buildContext(
                buildJsonDoc("{\"profile\":{\"age\":18}}"), List.of(profile, age));
        ProbeInvocationResult result = buildResult(
                200, "application/json", "{\"profile\":{\"age\":\"十八\"}}");

        assertThatThrownBy(() -> validator.validate(context, result))
                .isInstanceOfSatisfying(InterfacePublishProbeException.class, exception ->
                        assertThat(exception.getStage()).isEqualTo(PublishProbeFailureStageEnum.RESPONSE_STRUCTURE));
    }

    /**
     * 构造 JSON 文档主记录。
     *
     * @return 文档主记录
     */
    private InterfaceDoc buildJsonDoc() {
        return buildJsonDoc("{\"name\":\"张三\"}");
    }

    /**
     * 构造指定成功示例的 JSON 文档主记录。
     *
     * @param successExample 成功示例
     * @return 文档主记录
     */
    private InterfaceDoc buildJsonDoc(String successExample) {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setResponseContentType("application/json");
        doc.setSuccessExample(successExample);
        return doc;
    }

    /**
     * 构造发布上下文。
     *
     * @param doc       文档主记录
     * @param docParams 文档参数
     * @return 发布上下文
     */
    private InterfacePublishContext buildContext(InterfaceDoc doc, List<InterfaceDocParam> docParams) {
        InterfacePublishContext context = new InterfacePublishContext();
        context.setInterfaceDoc(doc);
        context.setDocParams(docParams);
        return context;
    }

    /**
     * 构造探测响应元数据。
     *
     * @param statusCode  状态码
     * @param contentType 媒体类型
     * @param body        响应体
     * @return 探测响应元数据
     */
    private ProbeInvocationResult buildResult(int statusCode, String contentType, String body) {
        ProbeInvocationResult result = new ProbeInvocationResult();
        result.setStatusCode(statusCode);
        result.setContentType(contentType);
        result.setBody(body);
        return result;
    }

    /**
     * 构造响应字段。
     *
     * @param id   字段 ID
     * @param name 字段名称
     * @param type 字段类型
     * @return 响应字段
     */
    private InterfaceDocParam buildResponseParam(Long id, String name, String type) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setId(id);
        param.setName(name);
        param.setType(type);
        param.setNullable(0);
        param.setParamScene(InterfaceDocParamSceneEnum.RESPONSE.getValue());
        return param;
    }
}
