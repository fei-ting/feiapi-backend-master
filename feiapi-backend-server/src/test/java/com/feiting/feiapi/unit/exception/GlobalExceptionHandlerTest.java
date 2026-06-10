package com.feiting.feiapi.unit.exception;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器测试
 */
@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    /**
     * 校验运行时异常不会把内部错误详情返回给前端
     */
    @Test
    @DisplayName("RuntimeException 返回通用系统错误信息")
    void shouldHideRuntimeExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException exception = new RuntimeException("SQL syntax error near user_password");

        BaseResponse<?> response = handler.runtimeExceptionHandler(exception);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo(ErrorCode.SYSTEM_ERROR.getMessage());
        assertThat(response.getMessage()).doesNotContain("SQL", "user_password");
    }
}
