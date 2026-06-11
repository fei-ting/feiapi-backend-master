package com.feiting.feiapi.unit.exception;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 全局异常处理器测试
 */
@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    /**
     * 被测全局异常处理器
     */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * 校验业务异常保持业务错误码和提示
     */
    @Test
    @DisplayName("BusinessException 返回业务错误信息")
    void shouldReturnBusinessExceptionMessage() {
        com.feiting.feiapi.exception.BusinessException exception =
                new com.feiting.feiapi.exception.BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该资源");

        BaseResponse<?> response = handler.businessExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.NO_AUTH_ERROR, "无权访问该资源");
    }

    /**
     * 校验请求体解析失败返回统一参数错误响应
     */
    @Test
    @DisplayName("HttpMessageNotReadableException 返回请求参数格式错误")
    void shouldReturnParamErrorWhenBodyNotReadable() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON parse error");

        BaseResponse<?> response = handler.httpMessageNotReadableExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "请求参数格式错误");
    }

    /**
     * 校验请求体参数校验失败返回字段错误信息
     */
    @Test
    @DisplayName("MethodArgumentNotValidException 返回字段校验错误")
    void shouldReturnFieldErrorWhenRequestBodyInvalid() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = createBindingResult("name", "不能为空");
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(createMethodParameter(), bindingResult);

        BaseResponse<?> response = handler.methodArgumentNotValidExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "name: 不能为空");
    }

    /**
     * 校验表单或查询参数绑定失败返回字段错误信息
     */
    @Test
    @DisplayName("BindException 返回字段绑定错误")
    void shouldReturnFieldErrorWhenBindingFailed() {
        BeanPropertyBindingResult bindingResult = createBindingResult("pageSize", "必须大于 0");
        BindException exception = new BindException(bindingResult);

        BaseResponse<?> response = handler.bindExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "pageSize: 必须大于 0");
    }

    /**
     * 校验约束校验失败返回统一参数错误响应
     */
    @Test
    @DisplayName("ConstraintViolationException 返回约束校验错误")
    void shouldReturnConstraintViolationMessage() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("必须大于 0");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        BaseResponse<?> response = handler.constraintViolationExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "id: 必须大于 0");
    }

    /**
     * 校验缺少请求参数返回参数名
     */
    @Test
    @DisplayName("MissingServletRequestParameterException 返回缺少请求参数")
    void shouldReturnMissingRequestParameterName() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("id", "Long");

        BaseResponse<?> response = handler.missingServletRequestParameterExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "缺少请求参数: id");
    }

    /**
     * 校验缺少请求头返回请求头名称
     */
    @Test
    @DisplayName("MissingRequestHeaderException 返回缺少请求头")
    void shouldReturnMissingRequestHeaderName() throws NoSuchMethodException {
        MissingRequestHeaderException exception =
                new MissingRequestHeaderException("accessKey", createMethodParameter());

        BaseResponse<?> response = handler.missingRequestHeaderExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "缺少请求头: accessKey");
    }

    /**
     * 校验缺少路径变量返回变量名
     */
    @Test
    @DisplayName("MissingPathVariableException 返回缺少路径变量")
    void shouldReturnMissingPathVariableName() throws NoSuchMethodException {
        MissingPathVariableException exception =
                new MissingPathVariableException("id", createMethodParameter());

        BaseResponse<?> response = handler.missingPathVariableExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "缺少路径变量: id");
    }

    /**
     * 校验请求绑定兜底异常返回统一参数错误
     */
    @Test
    @DisplayName("ServletRequestBindingException 返回请求参数绑定失败")
    void shouldReturnParamErrorWhenServletRequestBindingFailed() {
        ServletRequestBindingException exception = new ServletRequestBindingException("绑定失败");

        BaseResponse<?> response = handler.servletRequestBindingExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "请求参数绑定失败");
    }

    /**
     * 校验参数类型不匹配返回参数名
     */
    @Test
    @DisplayName("MethodArgumentTypeMismatchException 返回参数类型错误")
    void shouldReturnTypeMismatchParameterName() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", createMethodParameter(), null);

        BaseResponse<?> response = handler.methodArgumentTypeMismatchExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "参数类型错误: id");
    }

    /**
     * 校验请求方法不支持返回统一参数错误响应
     */
    @Test
    @DisplayName("HttpRequestMethodNotSupportedException 返回请求方法不支持")
    void shouldReturnMethodNotSupportedMessage() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("TRACE", List.of("GET", "POST"));

        BaseResponse<?> response = handler.httpRequestMethodNotSupportedExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "请求方法不支持: TRACE");
    }

    /**
     * 校验请求媒体类型不支持返回统一参数错误响应
     */
    @Test
    @DisplayName("HttpMediaTypeNotSupportedException 返回请求媒体类型不支持")
    void shouldReturnMediaTypeNotSupportedMessage() {
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));

        BaseResponse<?> response = handler.httpMediaTypeNotSupportedExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "请求媒体类型不支持");
    }

    /**
     * 校验响应媒体类型不可接受返回统一参数错误响应
     */
    @Test
    @DisplayName("HttpMediaTypeNotAcceptableException 返回响应媒体类型不可接受")
    void shouldReturnMediaTypeNotAcceptableMessage() {
        HttpMediaTypeNotAcceptableException exception =
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

        BaseResponse<?> response = handler.httpMediaTypeNotAcceptableExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.PARAMS_ERROR, "响应媒体类型不可接受");
    }

    /**
     * 校验请求路径不存在返回统一 404 错误响应
     */
    @Test
    @DisplayName("NoResourceFoundException 返回请求路径不存在")
    void shouldReturnNotFoundWhenResourceMissing() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "/missing");

        BaseResponse<?> response = handler.notFoundExceptionHandler(exception);

        assertErrorResponse(response, ErrorCode.NOT_FOUND_ERROR, "请求路径不存在");
    }

    /**
     * 校验运行时异常不会把内部错误详情返回给前端
     */
    @Test
    @DisplayName("RuntimeException 返回通用系统错误信息")
    void shouldHideRuntimeExceptionMessage() {
        RuntimeException exception = new RuntimeException("SQL syntax error near user_password");

        BaseResponse<?> response = handler.runtimeExceptionHandler(exception);

        assertThat(response.getCode()).isEqualTo(ErrorCode.SYSTEM_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo(ErrorCode.SYSTEM_ERROR.getMessage());
        assertThat(response.getMessage()).doesNotContain("SQL", "user_password");
    }

    /**
     * 创建字段绑定结果
     */
    private BeanPropertyBindingResult createBindingResult(String field, String message) {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", field, message));
        return bindingResult;
    }

    /**
     * 创建方法参数对象
     */
    private MethodParameter createMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class);
        return new MethodParameter(method, 0);
    }

    /**
     * 作为异常构造器使用的样例方法
     */
    private void sampleMethod(String value) {
    }

    /**
     * 断言错误响应结构
     */
    private void assertErrorResponse(BaseResponse<?> response, ErrorCode errorCode, String message) {
        assertThat(response.getCode()).isEqualTo(errorCode.getCode());
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getData()).isNull();
    }
}
