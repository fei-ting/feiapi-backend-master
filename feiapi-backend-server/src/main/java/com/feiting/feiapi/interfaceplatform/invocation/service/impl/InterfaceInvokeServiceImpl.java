package com.feiting.feiapi.interfaceplatform.invocation.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.component.InterfaceRequestParamValidator;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.invocation.model.vo.InterfaceInvokeResultVO;
import com.feiting.feiapi.interfaceplatform.invocation.service.api.InterfaceInvokeService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapiclientsdk.model.OnlineDebugInvocationResult;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 在线调试调用服务实现。
 */
@Service
public class InterfaceInvokeServiceImpl implements InterfaceInvokeService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(InterfaceInvokeServiceImpl.class);

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 平台请求参数模板校验器。
     */
    private final InterfaceRequestParamValidator interfaceRequestParamValidator;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 网关地址。
     */
    private final String gatewayHost;

    /**
     * 创建在线调试调用服务。
     *
     * @param interfaceInfoService           接口信息服务
     * @param interfaceRequestParamValidator 平台请求参数模板校验器
     * @param sdkMethodRegistry              SDK 方法注册器
     * @param gatewayHost                    网关地址
     */
    public InterfaceInvokeServiceImpl(InterfaceInfoService interfaceInfoService,
                                      InterfaceRequestParamValidator interfaceRequestParamValidator,
                                      SdkMethodRegistry sdkMethodRegistry,
                                      @Value("${feiapi.client.gateway-host}") String gatewayHost) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceRequestParamValidator = interfaceRequestParamValidator;
        this.sdkMethodRegistry = sdkMethodRegistry;
        this.gatewayHost = gatewayHost;
    }

    /**
     * 使用当前登录用户的 APIKey 通过 SDK 发起真实接口调用。
     *
     * @param interfaceInfoId   接口信息 ID
     * @param userRequestParams 用户请求参数 JSON
     * @param loginUser         当前登录用户
     * @return 在线调试调用结果
     */
    @Override
    public InterfaceInvokeResultVO invoke(long interfaceInfoId, String userRequestParams, User loginUser) {
        InterfaceInfo interfaceInfo = getInvocableInterface(interfaceInfoId);
        interfaceRequestParamValidator.validate(interfaceInfo.getRequestParams(), userRequestParams);
        validateLoginUserApiKey(loginUser);

        FeiApiClient client = new FeiApiClient(loginUser.getAccessKey(), loginUser.getSecretKey(), gatewayHost);
        long startNanos = System.nanoTime();
        try {
            client.enableOnlineDebugMode();
            Object invocationValue = sdkMethodRegistry.invoke(
                    client,
                    getRequiredSdkMethodName(interfaceInfo),
                    userRequestParams);
            OnlineDebugInvocationResult sdkResult = client.getOnlineDebugInvocationResult();
            return sdkResult == null
                    ? buildResultWithoutHttpResponse(invocationValue, elapsedMillis(startNanos))
                    : buildHttpResult(sdkResult);
        } catch (RuntimeException exception) {
            log.warn("在线调试调用失败，interfaceInfoId={}", interfaceInfoId, exception);
            return buildExecutionFailure(exception, elapsedMillis(startNanos));
        } finally {
            client.disableOnlineDebugMode();
        }
    }

    /**
     * 查询并校验可在线调用的接口。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 已上线接口信息
     */
    private InterfaceInfo getInvocableInterface(long interfaceInfoId) {
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.ONLINE.getValue())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口未上线或正在发布验证中");
        }
        return interfaceInfo;
    }

    /**
     * 校验当前登录用户是否具备完整 APIKey。
     *
     * @param loginUser 当前登录用户
     */
    private void validateLoginUserApiKey(User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (StringUtils.isAnyBlank(loginUser.getAccessKey(), loginUser.getSecretKey())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前账号 APIKey 配置异常");
        }
    }

    /**
     * 获取接口绑定的 SDK 方法名。
     *
     * @param interfaceInfo 接口信息
     * @return SDK 方法名
     */
    private String getRequiredSdkMethodName(InterfaceInfo interfaceInfo) {
        if (StringUtils.isBlank(interfaceInfo.getSdkMethodName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口未配置 SDK 方法名");
        }
        return interfaceInfo.getSdkMethodName().trim();
    }

    /**
     * 将 SDK 捕获的 HTTP 响应转换为页面结果。
     *
     * @param sdkResult SDK 在线调试响应元数据
     * @return 页面结果
     */
    private InterfaceInvokeResultVO buildHttpResult(OnlineDebugInvocationResult sdkResult) {
        Integer statusCode = sdkResult.getStatusCode();
        InterfaceInvokeResultVO result = new InterfaceInvokeResultVO();
        result.setSuccessful(statusCode != null && statusCode >= 200 && statusCode < 300);
        result.setStatusCode(statusCode);
        result.setDurationMs(Objects.requireNonNullElse(sdkResult.getDurationMs(), 0L));
        result.setContentType(sdkResult.getContentType());
        result.setBody(sdkResult.getBody());
        return result;
    }

    /**
     * 构建未产生 HTTP 元数据但 SDK 方法正常返回时的兼容结果。
     *
     * @param invocationValue SDK 方法返回值
     * @param durationMs      调用耗时
     * @return 页面结果
     */
    private InterfaceInvokeResultVO buildResultWithoutHttpResponse(Object invocationValue, long durationMs) {
        InterfaceInvokeResultVO result = new InterfaceInvokeResultVO();
        result.setSuccessful(Boolean.TRUE);
        result.setDurationMs(durationMs);
        result.setBody(invocationValue == null ? "" : String.valueOf(invocationValue));
        return result;
    }

    /**
     * 将未产生 HTTP 响应的执行异常转换为安全页面结果。
     *
     * @param exception  原始执行异常
     * @param durationMs 调用耗时
     * @return 页面结果
     */
    private InterfaceInvokeResultVO buildExecutionFailure(RuntimeException exception, long durationMs) {
        InterfaceInvokeResultVO result = new InterfaceInvokeResultVO();
        result.setSuccessful(Boolean.FALSE);
        result.setDurationMs(durationMs);
        result.setErrorMessage(hasCause(exception, SocketTimeoutException.class)
                ? "接口调用超时，请稍后重试"
                : "接口调用失败，请稍后重试");
        return result;
    }

    /**
     * 判断异常链中是否存在指定异常类型。
     *
     * @param throwable 原始异常
     * @param type      目标异常类型
     * @return 是否存在目标异常
     */
    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 计算从指定时间点到当前的毫秒耗时。
     *
     * @param startNanos 开始时间纳秒值
     * @return 非负毫秒耗时
     */
    private long elapsedMillis(long startNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }
}
