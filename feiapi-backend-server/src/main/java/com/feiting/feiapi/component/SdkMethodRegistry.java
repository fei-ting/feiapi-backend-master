package com.feiting.feiapi.component;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * SDK 方法注册器。
 *
 * 只收录带 {@link SdkInvoke} 注解的方法，避免通过方法名反射调用任意公开方法。
 */
@Component
public class SdkMethodRegistry {

    private final Map<String, Method> methodMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (Method method : FeiApiClient.class.getDeclaredMethods()) {
            SdkInvoke sdkInvoke = method.getAnnotation(SdkInvoke.class);
            if (sdkInvoke == null) {
                continue;
            }
            method.setAccessible(true);
            methodMap.put(method.getName(), method);
        }
    }

    public Object invoke(FeiApiClient client, String methodName, String requestParams) {
        Method method = methodMap.get(methodName);
        if (method == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的接口方法：" + methodName);
        }

        SdkInvoke sdkInvoke = method.getAnnotation(SdkInvoke.class);
        boolean needParams = sdkInvoke != null && sdkInvoke.needParams();
        try {
            if (needParams) {
                if (requestParams == null || requestParams.trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口方法参数不能为空：" + methodName);
                }
                return method.invoke(client, requestParams);
            }

            if (requestParams != null && !requestParams.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口方法不需要参数：" + methodName);
            }
            return method.invoke(client);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "接口调用失败：" + methodName + "，原因：" + errorMsg);
        }
    }

    public Map<String, Method> getMethodMap() {
        return Collections.unmodifiableMap(methodMap);
    }
}
