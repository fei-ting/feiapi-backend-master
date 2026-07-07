package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.component.InterfaceRequestParamValidator;
import com.feiting.feiapi.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口请求参数校验器单元测试。
 */
@DisplayName("InterfaceRequestParamValidator 单元测试")
class InterfaceRequestParamValidatorTest {

    private final InterfaceRequestParamValidator validator = new InterfaceRequestParamValidator();

    @Nested
    @DisplayName("JSON 合法性校验")
    class JsonSyntaxTests {

        @Test
        @DisplayName("用户请求参数不是合法 JSON 时返回参数错误")
        void shouldRejectInvalidJson() {
            assertThatThrownBy(() -> validator.validate("", "{\"name\":\"test\""))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请求参数必须是合法 JSON");
        }
    }

    @Nested
    @DisplayName("字段级校验")
    class FieldValidationTests {

        @Test
        @DisplayName("按当前接口模板校验必填字段")
        void shouldRejectMissingRequiredField() {
            assertThatThrownBy(() -> validator.validate("{\"username\":\"string\"}", "{\"ip\":\"8.8.8.8\"}"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请求参数缺少必填字段：username");
        }

        @Test
        @DisplayName("不同接口模板只校验自身字段")
        void shouldValidateFieldsByCurrentInterfaceTemplate() {
            assertThatCode(() -> validator.validate("{\"ip\":\"string\"}", "{\"ip\":\"8.8.8.8\"}"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("字段类型不匹配时返回明确提示")
        void shouldRejectWrongFieldType() {
            assertThatThrownBy(() -> validator.validate("{\"ip\":\"string\"}", "{\"ip\":123}"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请求参数字段类型错误：ip 应为 string");
        }

        @Test
        @DisplayName("字符串字段为空白时返回明确提示")
        void shouldRejectBlankStringField() {
            assertThatThrownBy(() -> validator.validate("{\"username\":\"string\"}", "{\"username\":\"   \"}"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请求参数字段不能为空：username");
        }

        @Test
        @DisplayName("模板要求字段但用户参数为空时返回缺少字段")
        void shouldRejectEmptyUserParamsWhenTemplateRequiresFields() {
            assertThatThrownBy(() -> validator.validate("{\"username\":\"string\"}", ""))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请求参数缺少必填字段：username");
        }

        @Test
        @DisplayName("模板要求对象但用户参数不是对象时返回明确提示")
        void shouldRejectNonObjectUserParamsWhenTemplateIsObject() {
            assertThatThrownBy(() -> validator.validate("{\"username\":\"string\"}", "[]"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("请求参数必须是 JSON 对象");
        }
    }

    @Nested
    @DisplayName("历史模板兼容")
    class LegacyTemplateTests {

        @Test
        @DisplayName("接口未配置请求参数模板时不做字段级校验")
        void shouldSkipFieldValidationWhenTemplateBlank() {
            assertThatCode(() -> validator.validate("", "{\"ip\":\"8.8.8.8\"}"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("接口请求参数模板不是 JSON 对象时不做字段级校验")
        void shouldSkipFieldValidationWhenTemplateNotObject() {
            assertThatCode(() -> validator.validate("username=string", "{\"ip\":\"8.8.8.8\"}"))
                    .doesNotThrowAnyException();
        }
    }
}
