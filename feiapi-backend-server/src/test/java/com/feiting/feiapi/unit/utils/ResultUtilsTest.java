package com.feiting.feiapi.unit.utils;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResultUtils 返回工具类测试")
class ResultUtilsTest {

    @Nested
    @DisplayName("success 方法")
    class SuccessTests {

        @Test
        @DisplayName("成功响应 code 为 0，message 为 ok")
        void shouldReturnSuccessResponse() {
            BaseResponse<String> response = ResultUtils.success("hello");

            assertEquals(0, response.getCode());
            assertEquals("hello", response.getData());
            assertEquals("ok", response.getMessage());
        }

        @Test
        @DisplayName("data 为 null 时正常返回")
        void shouldHandleNullData() {
            BaseResponse<Object> response = ResultUtils.success(null);

            assertEquals(0, response.getCode());
            assertNull(response.getData());
            assertEquals("ok", response.getMessage());
        }

        @Test
        @DisplayName("data 为数字类型")
        void shouldHandleLongData() {
            BaseResponse<Long> response = ResultUtils.success(12345L);

            assertEquals(0, response.getCode());
            assertEquals(12345L, response.getData());
        }
    }

    @Nested
    @DisplayName("error 方法")
    class ErrorTests {

        @Test
        @DisplayName("ErrorCode 枚举返回对应 code 和 message")
        void shouldReturnErrorResponseFromErrorCode() {
            BaseResponse<?> response = ResultUtils.error(ErrorCode.PARAMS_ERROR);

            assertEquals(40000, response.getCode());
            assertEquals("请求参数错误", response.getMessage());
            assertNull(response.getData());
        }

        @Test
        @DisplayName("NOT_LOGIN_ERROR 返回正确状态码")
        void shouldReturnNotLoginError() {
            BaseResponse<?> response = ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);

            assertEquals(40100, response.getCode());
        }

        @Test
        @DisplayName("NO_AUTH_ERROR 返回正确状态码")
        void shouldReturnNoAuthError() {
            BaseResponse<?> response = ResultUtils.error(ErrorCode.NO_AUTH_ERROR);

            assertEquals(40101, response.getCode());
        }

        @Test
        @DisplayName("SYSTEM_ERROR 返回正确状态码")
        void shouldReturnSystemError() {
            BaseResponse<?> response = ResultUtils.error(ErrorCode.SYSTEM_ERROR);

            assertEquals(50000, response.getCode());
        }

        @Test
        @DisplayName("自定义 code 和 message 的 error")
        void shouldReturnCustomError() {
            BaseResponse<?> response = ResultUtils.error(12345, "自定义错误");

            assertEquals(12345, response.getCode());
            assertEquals("自定义错误", response.getMessage());
            assertNull(response.getData());
        }

        @Test
        @DisplayName("ErrorCode + 自定义 message 覆盖默认 message")
        void shouldOverrideMessageWithErrorCodeAndCustomMessage() {
            BaseResponse<?> response = ResultUtils.error(ErrorCode.SYSTEM_ERROR, "自定义系统错误");

            assertEquals(50000, response.getCode());
            assertEquals("自定义系统错误", response.getMessage());
        }
    }
}
