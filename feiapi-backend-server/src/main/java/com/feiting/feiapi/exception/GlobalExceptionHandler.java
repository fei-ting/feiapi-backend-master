package com.feiting.feiapi.exception;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import com.feiting.feiapi.model.vo.InterfacePublishCheckVO;
import com.feiting.feiapi.model.vo.InterfacePublishProbeFailureVO;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.FieldError;
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
 * @author feiting
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理接口发布前静态检查失败。
     *
     * @param e 发布前检查失败异常
     * @return 带检查问题列表的错误响应
     */
    @ExceptionHandler(InterfacePublishCheckException.class)
    public BaseResponse<InterfacePublishCheckVO> interfacePublishCheckExceptionHandler(InterfacePublishCheckException e) {
        log.warn("interfacePublishCheckException: {}", e.getMessage());
        InterfacePublishCheckVO checkVO = new InterfacePublishCheckVO();
        checkVO.setPassed(false);
        checkVO.setIssues(e.getIssues());
        return new BaseResponse<>(ErrorCode.PUBLISH_CHECK_FAILED.getCode(), checkVO, e.getMessage());
    }

    /**
     * 处理接口发布探测失败。
     *
     * @param e 发布探测失败异常
     * @return 带探测阶段的错误响应
     */
    @ExceptionHandler(InterfacePublishProbeException.class)
    public BaseResponse<InterfacePublishProbeFailureVO> interfacePublishProbeExceptionHandler(InterfacePublishProbeException e) {
        log.warn("interfacePublishProbeException: {}", e.getMessage());
        InterfacePublishProbeFailureVO failureVO = new InterfacePublishProbeFailureVO();
        failureVO.setStage(e.getStage().name());
        failureVO.setReason(e.getReason());
        return new BaseResponse<>(ErrorCode.PUBLISH_PROBE_FAILED.getCode(), failureVO, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("businessException: " + e.getMessage(), e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 请求正文超过业务允许上限时返回 HTTP 413。
     *
     * @param e 请求正文超限异常
     * @return HTTP 413 错误响应
     */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<BaseResponse<?>> requestBodyTooLargeExceptionHandler(RequestBodyTooLargeException e) {
        log.warn("请求体过大: {}", e.getMessage());
        return buildPayloadTooLargeResponse(e.getMessage());
    }

    /**
     * 请求体无法解析（JSON 格式错误、必填字段缺失等），归类为参数错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        RequestBodyTooLargeException tooLargeException = findCause(e, RequestBodyTooLargeException.class);
        if (tooLargeException != null) {
            log.warn("请求体读取超过限制: {}", tooLargeException.getMessage());
            return buildPayloadTooLargeResponse(tooLargeException.getMessage());
        }
        log.warn("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数格式错误"));
    }

    /**
     * 请求体参数校验失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        String message = buildBindingErrorMessage(e.getBindingResult());
        if (isOnlineInvokeBodyTooLarge(e.getBindingResult())) {
            log.warn("在线调用请求体超过限制: {}", message);
            return buildPayloadTooLargeResponse("请求体不能超过 65535 字节");
        }
        log.warn("请求体参数校验失败: {}", message);
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.PARAMS_ERROR, message));
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
     * 判断绑定错误是否来自在线调用正文的 UTF-8 字节上限。
     *
     * @param bindingResult 参数绑定结果
     * @return 是否属于在线调用请求体超限
     */
    private boolean isOnlineInvokeBodyTooLarge(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .anyMatch(this::isOnlineInvokeBodyTooLarge);
    }

    /**
     * 判断字段错误是否为在线调用正文 UTF-8 字节约束。
     * <p>
     * 通过同时匹配字段名 {@code userRequestParams} 和约束类型 {@code Utf8ByteLength}
     * 来精确定位在线调用请求体超限场景。如果未来其他 DTO 字段也使用 {@code @Utf8ByteLength}
     * 注解（如 JSON 示例字段），此处不会误匹配，因为字段名不同。
     * </p>
     *
     * @param fieldError 字段错误
     * @return 是否属于在线调用请求体超限
     */
    private boolean isOnlineInvokeBodyTooLarge(FieldError fieldError) {
        return "userRequestParams".equals(fieldError.getField())
                && "Utf8ByteLength".equals(fieldError.getCode());
    }

    /**
     * 构造统一的 HTTP 413 JSON 响应。
     *
     * @param message 可公开的超限提示
     * @return HTTP 413 响应
     */
    private ResponseEntity<BaseResponse<?>> buildPayloadTooLargeResponse(String message) {
        BaseResponse<?> response = ResultUtils.error(ErrorCode.REQUEST_BODY_TOO_LARGE_ERROR, message);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    /**
     * 在异常原因链中查找指定类型异常。
     *
     * @param throwable 异常
     * @param causeType 原因类型
     * @param <T>       原因类型参数
     * @return 找到的原因，未找到时返回 null
     */
    private <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return causeType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 判断文本是否包含有效字符
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
