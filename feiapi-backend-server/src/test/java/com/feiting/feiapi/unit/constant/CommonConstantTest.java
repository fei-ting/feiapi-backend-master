package com.feiting.feiapi.unit.constant;

import com.feiting.feiapi.constant.CommonConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通用常量测试
 */
@DisplayName("CommonConstant 单元测试")
class CommonConstantTest {

    /**
     * 校验降序排序常量值
     */
    @Test
    @DisplayName("降序排序常量不包含前导空格")
    void shouldDefineDescSortOrderWithoutLeadingSpace() {
        assertThat(CommonConstant.SORT_ORDER_DESC).isEqualTo("descend");
    }
}
