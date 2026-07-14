package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口文档内容安全校验器单元测试。
 */
@DisplayName("InterfaceDocContentSecurityValidator 单元测试")
class InterfaceDocContentSecurityValidatorTest {

    /** 被测内容安全校验器。 */
    private final InterfaceDocContentSecurityValidator validator = new InterfaceDocContentSecurityValidator();

    /** DTO 参数校验器，使用 static final 避免重复创建。 */
    private static final Validator BEAN_VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 验证常见未脱敏凭据会被拒绝。
     *
     * @param text 待校验文本
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "accessKey: abcdefgh12345678",
            "secret_key = abc$123!xyz",
            "token: Bearer eyJhbGciOiJIUzI1NiJ9",
            "Authorization: Basic dXNlcjpwYXNz",
            "password: P@ssw0rd!"
    })
    @DisplayName("拒绝常见未脱敏凭据")
    void shouldRejectUnmaskedCredentials(String text) {
        assertThatThrownBy(() -> validator.validateText(text))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档内容不能包含未脱敏敏感信息");
    }

    /**
     * 验证手机号、邮箱和身份证号会被拒绝。
     *
     * @param text 待校验文本
     */
    @ParameterizedTest
    @ValueSource(strings = {"联系电话 13800138000", "邮箱 user@example.com", "身份证 11010519491231002X"})
    @DisplayName("拒绝高置信度个人敏感信息")
    void shouldRejectPersonalSensitiveInformation(String text) {
        assertThatThrownBy(() -> validator.validateText(text))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档内容不能包含未脱敏敏感信息");
    }

    /**
     * 验证白名单占位符允许保存。
     *
     * @param text 待校验文本
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "token: ***", "token: <TOKEN>", "token: ${TOKEN}",
            "Authorization: Bearer <TOKEN>", "authorization: bearer ${TOKEN}",
            "Authorization: BEARER ${TOKEN}", "Authorization: Basic ${TOKEN}",
            "accessKey: <ACCESS_KEY>", "accessKey: ${ACCESS_KEY}",
            "secretKey: <SECRET_KEY>", "secretKey: ${SECRET_KEY}",
            "password: <PASSWORD>", "password: ${PASSWORD}",
            "token: <MASKED>", "token: ${MASKED}"
    })
    @DisplayName("允许明确的脱敏占位符")
    void shouldAllowMaskPlaceholders(String text) {
        assertThatCode(() -> validator.validateText(text)).doesNotThrowAnyException();
    }

    /**
     * 验证 JSON 敏感字段会按标准化字段名递归识别。
     *
     * @param json JSON 示例
     */
    /**
     * 验证 JSON 敏感字段会按标准化字段名递归识别。
     * 注意：true、{}、[] 等非字符串值也会被拒绝，因为敏感字段只允许 null、空字符串或明确的脱敏占位符。
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"access_key\":\"real-key\"}",           // 明文密钥
            "{\"ｔｏｋｅｎ\":\"real-token\"}",              // 全角字符标准化后匹配 token
            "{\"data\":{\"refresh-token\":\"real-token\"}}", // 嵌套对象中的敏感字段
            "{\"items\":[{\"密码\":123}]}",             // 数组中的中文敏感字段
            "{\"secretKey\":true}",                    // 布尔值不是合法的脱敏占位符
            "{\"token\":{}}",                          // 空对象不是合法的脱敏占位符
            "{\"password\":[]}"                        // 空数组不是合法的脱敏占位符
    })
    @DisplayName("拒绝 JSON 中各类未脱敏敏感字段值")
    void shouldRejectSensitiveJsonFieldValues(String json) {
        assertThatThrownBy(() -> validator.validateJsonExample(json, "JSON 非法"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档内容不能包含未脱敏敏感信息");
    }

    /**
     * 验证空敏感值、占位符和相似业务字段允许保存。
     */
    @Test
    @DisplayName("允许空敏感值、占位符和非敏感相似字段")
    void shouldAllowSafeSensitiveJsonValuesAndSimilarFields() {
        String json = "{\"token\":null,\"password\":\"\",\"access_key\":\"<ACCESS_KEY>\","
                + "\"tokenCount\":10,\"passwordHint\":\"请填写登录信息\"}";

        assertThatCode(() -> validator.validateJsonExample(json, "JSON 非法"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证空 JSON 容器和十层嵌套允许扫描。
     */
    @Test
    @DisplayName("允许空 JSON 容器和十层嵌套")
    void shouldAllowEmptyContainersAndTenLevels() {
        String nestedJson = IntStream.range(0, 10).mapToObj(index -> "{\"data\":")
                .collect(Collectors.joining()) + "\"安全内容\"" + "}".repeat(10);

        assertThatCode(() -> validator.validateJsonExample("{}", "JSON 非法")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateJsonExample("[]", "JSON 非法")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateJsonExample(nestedJson, "JSON 非法")).doesNotThrowAnyException();
    }

    /**
     * 验证超过安全扫描深度的 JSON 会被拒绝。
     */
    @Test
    @DisplayName("拒绝超过六十四层的 JSON")
    void shouldRejectJsonDeeperThanSixtyFourLevels() {
        String maximumDepthJson = IntStream.range(0, 64).mapToObj(index -> "{\"data\":")
                .collect(Collectors.joining()) + "null" + "}".repeat(64);
        String nestedJson = IntStream.range(0, 65).mapToObj(index -> "{\"data\":")
                .collect(Collectors.joining()) + "null" + "}".repeat(65);

        assertThatCode(() -> validator.validateJsonExample(maximumDepthJson, "JSON 非法"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateJsonExample(nestedJson, "JSON 非法"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("JSON 示例嵌套深度不能超过 64");
    }

    /**
     * 验证超长但合法的示例可以完成扫描。
     */
    @Test
    @DisplayName("六万五千五百三十五字符示例可以完成扫描")
    void shouldScanMaximumLengthExample() {
        String json = "{\"data\":\"" + "a".repeat(65524) + "\"}";

        assertThatCode(() -> validator.validateJsonExample(json, "JSON 非法"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证超过 DTO 长度上限的 JSON 示例会在请求参数校验阶段被拒绝。
     * 使用精确断言：验证恰好有 1 个违规，且违规消息正确。
     */
    @Test
    @DisplayName("六万五千五百三十六字符示例由 DTO 长度校验拒绝")
    void shouldRejectExampleLongerThanDtoLimit() {
        InterfaceDocSaveRequest request = new InterfaceDocSaveRequest();
        request.setInterfaceInfoId(1L);
        request.setDocVersion("v1.0");
        request.setRequestContentType("application/json");
        request.setResponseContentType("application/json");
        request.setSuccessExample("a".repeat(65536));

        assertThat(BEAN_VALIDATOR.validate(request))
                .hasSize(1)
                .first()
                .satisfies(violation -> assertThat(violation.getMessage()).isEqualTo("成功响应示例长度不能超过 65535"));
    }

    /**
     * 验证非法 JSON 使用调用方指定提示。
     */
    @Test
    @DisplayName("非法 JSON 返回指定参数错误")
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> validator.validateJsonExample("{", "成功响应示例必须是合法 JSON"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("成功响应示例必须是合法 JSON");
    }

    /**
     * 验证常见脚本形式会被拒绝。
     *
     * @param validationRule 校验规则
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "onclick=alert(1)", "<img/onerror=alert(1)>", "<img src=x onerror = alert(1)>",
            "\"onclick=alert(1)\"", "<SCRIPT>alert(1)</SCRIPT>", "javascript : alert(1)"
    })
    @DisplayName("拒绝校验规则中的脚本内容")
    void shouldRejectScriptContent(String validationRule) {
        assertThatThrownBy(() -> validator.validateValidationRule(validationRule))
                .isInstanceOf(BusinessException.class)
                .hasMessage("校验规则不能包含脚本内容");
    }

    /**
     * 验证内部关键词和服务器路径会被拒绝。
     *
     * @param solution 错误解决建议
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "检查Redis连接", "查看Dubbo日志", "检查 Nacos 服务状态", "通过JDBC查询数据表",
            "查看 C:\\logs\\server.log", "检查 /var/log/service.log", "检查数据库状态"
    })
    @DisplayName("拒绝错误解决建议中的内部实现信息")
    void shouldRejectInternalInformation(String solution) {
        assertThatThrownBy(() -> validator.validateSolution(solution))
                .isInstanceOf(BusinessException.class)
                .hasMessage("错误解决建议不能包含内部实现信息");
    }

    /**
     * 验证完整安全英文单词、空文本和普通说明不会误报。
     *
     * @param text 普通文本
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "redish 是业务字段", "databaseValue 是参数名", "请检查请求参数后重试"})
    @DisplayName("允许空文本和普通业务说明")
    void shouldAllowNormalText(String text) {
        assertThatCode(() -> validator.validateSolution(text)).doesNotThrowAnyException();
    }

    /**
     * 验证 null 输入不会抛出异常。
     */
    @Test
    @DisplayName("null 输入安全处理")
    void shouldHandleNullInput() {
        assertThatCode(() -> validator.validateText(null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateJsonExample(null, "JSON 非法")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateValidationRule(null)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateSolution(null)).doesNotThrowAnyException();
    }
}
