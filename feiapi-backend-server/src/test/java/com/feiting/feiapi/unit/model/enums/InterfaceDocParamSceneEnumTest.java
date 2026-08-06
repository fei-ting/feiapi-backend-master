package com.feiting.feiapi.unit.model.enums;

import com.feiting.feiapi.interfaceplatform.documentation.model.enums.InterfaceDocParamSceneEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口文档参数场景枚举测试。
 */
@DisplayName("接口文档参数场景枚举测试")
class InterfaceDocParamSceneEnumTest {

    /**
     * 精确场景值应解析为对应枚举。
     *
     * @param value        场景值
     * @param expectedName 预期枚举名称
     */
    @ParameterizedTest(name = "{0} 应解析为 {1}")
    @CsvSource({"QUERY,QUERY", "BODY,BODY", "RESPONSE,RESPONSE"})
    @DisplayName("精确场景值解析成功")
    void shouldResolveExactSceneValue(String value, String expectedName) {
        assertThat(InterfaceDocParamSceneEnum.getEnumByValue(value))
                .isEqualTo(InterfaceDocParamSceneEnum.valueOf(expectedName));
        assertThat(InterfaceDocParamSceneEnum.isValid(value)).isTrue();
    }

    /**
     * 非精确场景值不得被静默裁剪或纠正。
     *
     * @param value 非精确场景值
     */
    @ParameterizedTest(name = "拒绝非精确场景值：{0}")
    @NullAndEmptySource
    @ValueSource(strings = {" ", " QUERY", "BODY ", " RESPONSE ", "body", "HEADER"})
    @DisplayName("非精确场景值解析失败")
    void shouldRejectNonExactSceneValue(String value) {
        assertThat(InterfaceDocParamSceneEnum.getEnumByValue(value)).isNull();
        assertThat(InterfaceDocParamSceneEnum.isValid(value)).isFalse();
    }
}
