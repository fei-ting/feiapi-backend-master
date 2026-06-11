package com.feiting.feiapi.exception;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author yupi
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("businessException: " + e.getMessage(), e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 请求体无法解析（JSON 格式错误、必填字段缺失等），归类为参数错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数格式错误");
    }

    /**
     * 请求体参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResponse<?> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        String message = buildBindingErrorMessage(e.getBindingResult());
        log.warn("请求体参数校验失败: {}", message);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    /**
     * 表单或查询参数绑定失败
     */
    @ExceptionHandler(BindException.class)
    public BaseResponse<?> bindExceptionHandler(BindException e) {
        String message = buildBindingErrorMessage(e.getBindingResult());
        log.warn("请求参数绑定失败: {}", message);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    /**
     * 单个请求参数约束校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public BaseResponse<?> constraintViolationExceptionHandler(ConstraintViolationException e) {
        String message = buildConstraintViolationMessage(e);
        log.warn("请求参数约束校验失败: {}", message);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, message);
    }

    /**
     * Spring MVC 方法参数校验失败
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public BaseResponse<?> handlerMethodValidationExceptionHandler(HandlerMethodValidationException e) {
        String message = e.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(this::hasText)
                .collect(Collectors.joining("; "));
        String responseMessage = hasText(message) ? message : ErrorCode.PARAMS_ERROR.getMessage();
        log.warn("请求方法参数校验失败: {}", responseMessage);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, responseMessage);
    }

    /**
     * 缺少必填请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public BaseResponse<?> missingServletRequestParameterExceptionHandler(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "缺少请求参数: " + e.getParameterName());
    }

    /**
     * 缺少必填请求头
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public BaseResponse<?> missingRequestHeaderExceptionHandler(MissingRequestHeaderException e) {
        log.warn("缺少请求头: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "缺少请求头: " + e.getHeaderName());
    }

    /**
     * 缺少路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public BaseResponse<?> missingPathVariableExceptionHandler(MissingPathVariableException e) {
        log.warn("缺少路径变量: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "缺少路径变量: " + e.getVariableName());
    }

    /**
     * 请求绑定异常兜底处理
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public BaseResponse<?> servletRequestBindingExceptionHandler(ServletRequestBindingException e) {
        log.warn("请求绑定失败: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数绑定失败");
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponse<?> methodArgumentTypeMismatchExceptionHandler(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "参数类型错误: " + e.getName());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public BaseResponse<?> httpRequestMethodNotSupportedExceptionHandler(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求方法不支持: " + e.getMethod());
    }

    /**
     * 请求媒体类型不支持
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public BaseResponse<?> httpMediaTypeNotSupportedExceptionHandler(HttpMediaTypeNotSupportedException e) {
        log.warn("请求媒体类型不支持: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求媒体类型不支持");
    }

    /**
     * 响应媒体类型不可接受
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public BaseResponse<?> httpMediaTypeNotAcceptableExceptionHandler(HttpMediaTypeNotAcceptableException e) {
        log.warn("响应媒体类型不可接受: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "响应媒体类型不可接受");
    }

    /**
     * 请求路径不存在
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public BaseResponse<?> notFoundExceptionHandler(Exception e) {
        log.warn("请求路径不存在: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.NOT_FOUND_ERROR, "请求路径不存在");
    }

    /**
     * 运行时异常统一处理，避免向前端暴露内部错误细节
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("runtimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 构造参数绑定错误提示
     */
    private String buildBindingErrorMessage(BindingResult bindingResult) {
        String fieldMessage = bindingResult.getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (hasText(fieldMessage)) {
            return fieldMessage;
        }
        String globalMessage = bindingResult.getGlobalErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(this::hasText)
                .collect(Collectors.joining("; "));
        return hasText(globalMessage) ? globalMessage : ErrorCode.PARAMS_ERROR.getMessage();
    }

    /**
     * 构造约束校验错误提示
     */
    private String buildConstraintViolationMessage(ConstraintViolationException e) {
        if (e.getConstraintViolations() == null || e.getConstraintViolations().isEmpty()) {
            return ErrorCode.PARAMS_ERROR.getMessage();
        }
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return hasText(message) ? message : ErrorCode.PARAMS_ERROR.getMessage();
    }

    /**
     * 判断文本是否包含有效字符
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
