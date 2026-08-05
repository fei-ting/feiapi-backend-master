package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.interfaceplatform.definition.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运行时请求参数模板校验器测试。
 */
@DisplayName("RuntimeRequestParamTemplateValidator 测试")
class RuntimeRequestParamTemplateValidatorTest {

    /**
     * 运行时请求参数模板校验器。
     */
    private final RuntimeRequestParamTemplateValidator validator =
            new RuntimeRequestParamTemplateValidator();

    /**
     * 校验合法模板可以通过。
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{}", "{\"userId\":1,\"keyword\":\"手机\"}"})
    @DisplayName("空模板或参数名合法的 JSON 对象允许通过")
    void shouldAllowBlankOrValidObjectTemplate(String requestParams) {
        assertThatCode(() -> validator.validate(requestParams)).doesNotThrowAnyException();
    }

    /**
     * 校验非对象模板会被拒绝。
     */
    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"text\"", "{invalid"})
    @DisplayName("非 JSON 对象或非法 JSON 模板被拒绝")
    void shouldRejectNonObjectOrInvalidJsonTemplate(String requestParams) {
        assertThatThrownBy(() -> validator.validate(requestParams))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请求参数模板");
    }

    /**
     * 校验参数名不能依赖静默裁剪变为合法名称。
     */
    @ParameterizedTest
    @ValueSource(strings = {"{\"\":1}", "{\"   \":1}", "{\" userId\":1}", "{\"userId \":1}",
            "{\"\\tuserId\":1}", "{\"\\u00A0userId\":1}"})
    @DisplayName("空白或带首尾空白的参数名被拒绝")
    void shouldRejectBlankOrSurroundingWhitespaceParamName(String requestParams) {
        assertThatThrownBy(() -> validator.validate(requestParams))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参数名称");
    }

    /**
     * 校验错误消息会转义控制字符，避免污染日志结构。
     */
    @Test
    @DisplayName("非法参数名中的控制字符在错误消息中被转义")
    void shouldEscapeControlCharactersInErrorMessage() {
        assertThatThrownBy(() -> validator.validate("{\"\\tuserId\":1}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("\\tuserId")
                .hasMessageNotContaining("\tuserId");
    }

    /**
     * 校验运行时参数数量边界。
     */
    @Test
    @DisplayName("运行时参数数量超过 100 时被拒绝")
    void shouldRejectTooManyRuntimeParams() {
        StringBuilder requestParams = new StringBuilder("{");
        for (int index = 1; index <= 101; index++) {
            if (index > 1) {
                requestParams.append(',');
            }
            requestParams.append('"').append("field").append(index).append("\":1");
        }
        requestParams.append('}');

        assertThatThrownBy(() -> validator.validate(requestParams.toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求参数数量不能超过 100");
    }

    /**
     * 校验运行时参数名称使用 Unicode 码点计数。
     */
    @Test
    @DisplayName("运行时参数名称允许 128 个表情码点并拒绝第 129 个")
    void shouldCountRuntimeParamNameByUnicodeCodePoint() {
        String allowedName = "😀".repeat(128);
        String rejectedName = "😀".repeat(129);

        assertThatCode(() -> validator.validate("{\"" + allowedName + "\":1}"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("{\"" + rejectedName + "\":1}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("参数名称长度不能超过 128 个字符");
    }

    /**
     * 校验运行时模板请求体使用 UTF-8 字节计数。
     */
    @Test
    @DisplayName("运行时模板超过 65535 个 UTF-8 字节时被拒绝")
    void shouldRejectRuntimeTemplateExceedingUtf8ByteLimit() {
        String requestParams = "{\"field\":\"" + "中".repeat(22000) + "\"}";

        assertThatThrownBy(() -> validator.validate(requestParams))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求参数模板不能超过 65535 个 UTF-8 字节");
    }
}
