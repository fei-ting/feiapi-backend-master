package com.feiting.feiapiinterface.exception;

import com.feiting.feiapiinterface.model.vo.InterfaceErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;

/**
 * 接口服务全局异常处理器。
 *
 * <p>统一将真实接口服务中的常见异常转换为 JSON 响应，提升平台在线调用页的错误可读性。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数错误码。
     */
    private static final int PARAMS_ERROR_CODE = 40000;

    /**
     * 资源不存在错误码。
     */
    private static final int NOT_FOUND_ERROR_CODE = 40400;

    /**
     * 系统错误码。
     */
    private static final int SYSTEM_ERROR_CODE = 50000;

    /**
     * JSON 响应媒体类型，显式声明 UTF-8 避免在线调用结果区出现中文乱码。
     */
    private static final MediaType APPLICATION_JSON_UTF8 = MediaType.parseMediaType("application/json;charset=UTF-8");

    /**
     * 处理请求体参数校验异常。
     *
     * @param exception 请求体参数校验异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<InterfaceErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .min(Comparator.comparing(FieldError::getField)
                        .thenComparing(this::getFieldErrorPriority))
                .map(FieldError::getDefaultMessage)
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .orElse("请求参数不正确");
        return build(HttpStatus.BAD_REQUEST, PARAMS_ERROR_CODE, message);
    }

    /**
     * 处理请求参数约束异常。
     *
     * @param exception 请求参数约束异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<InterfaceErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .filter(errorMessage -> errorMessage != null && !errorMessage.isBlank())
                .findFirst()
                .orElse("请求参数不正确");
        return build(HttpStatus.BAD_REQUEST, PARAMS_ERROR_CODE, message);
    }

    /**
     * 处理请求体解析异常。
     *
     * @param exception 请求体解析异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<InterfaceErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return build(HttpStatus.BAD_REQUEST, PARAMS_ERROR_CODE, "请求体格式不正确");
    }

    /**
     * 处理缺少请求参数异常。
     *
     * @param exception 缺少请求参数异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<InterfaceErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        return build(HttpStatus.BAD_REQUEST, PARAMS_ERROR_CODE, "缺少请求参数：" + exception.getParameterName());
    }

    /**
     * 处理参数类型不匹配异常。
     *
     * @param exception 参数类型不匹配异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<InterfaceErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST, PARAMS_ERROR_CODE, "请求参数类型不正确：" + exception.getName());
    }

    /**
     * 处理接口主动抛出的 HTTP 状态异常。
     *
     * @param exception HTTP 状态异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<InterfaceErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        HttpStatus responseStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        int code = responseStatus == HttpStatus.NOT_FOUND ? NOT_FOUND_ERROR_CODE : PARAMS_ERROR_CODE;
        String message = exception.getReason();
        return build(responseStatus, code, message == null || message.isBlank() ? "接口请求失败" : message);
    }

    /**
     * 处理兜底系统异常。
     *
     * @param exception 系统异常
     * @return JSON 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<InterfaceErrorResponse> handleException(Exception exception) {
        log.error("接口服务异常", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, SYSTEM_ERROR_CODE, "接口服务异常，请稍后重试");
    }

    /**
     * 构建统一错误响应。
     *
     * @param status  HTTP 状态
     * @param code    业务错误码
     * @param message 错误提示
     * @return JSON 错误响应
     */
    private ResponseEntity<InterfaceErrorResponse> build(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status)
                .contentType(APPLICATION_JSON_UTF8)
                .body(new InterfaceErrorResponse(code, message, null));
    }

    /**
     * 获取字段错误提示优先级。
     *
     * <p>同一字段同时命中多个约束时，优先返回必填类错误，再返回范围或长度错误，降低调用方排查成本。</p>
     *
     * @param fieldError 字段错误
     * @return 错误优先级
     */
    private int getFieldErrorPriority(FieldError fieldError) {
        String code = fieldError.getCode();
        if ("NotBlank".equals(code) || "NotNull".equals(code) || "NotEmpty".equals(code)) {
            return 0;
        }
        return 1;
    }
}
