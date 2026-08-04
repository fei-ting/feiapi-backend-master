package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.component.InterfaceProbeResponseValidator;
import com.feiting.feiapi.exception.InterfacePublishProbeException;
import com.feiting.feiapi.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.model.publish.InterfacePublishContext;
import com.feiting.feiapi.service.InterfacePublishProbeService;
import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapiclientsdk.exception.ProbeResponseTooLargeException;
import com.feiting.feiapiclientsdk.model.ProbeInvocationResult;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 接口发布探测执行服务实现。
 */
@Service
public class InterfacePublishProbeServiceImpl implements InterfacePublishProbeService {

    /**
     * 探测总超时时间。
     */
    private static final long TOTAL_TIMEOUT_SECONDS = 15L;

    /**
     * 探测线程池核心线程数。
     */
    private static final int CORE_POOL_SIZE = 2;

    /**
     * 探测线程池最大线程数。
     */
    private static final int MAX_POOL_SIZE = 4;

    /**
     * 探测等待队列大小。
     */
    private static final int QUEUE_CAPACITY = 8;

    /**
     * SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 平台 SDK 客户端。
     */
    private final FeiApiClient feiApiClient;

    /**
     * 探测响应契约校验器。
     */
    private final InterfaceProbeResponseValidator responseValidator;

    /**
     * 发布探测专用有界线程池。
     */
    private final ThreadPoolExecutor probeExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("interface-publish-probe-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * 创建接口发布探测服务实现。
     *
     * @param sdkMethodRegistry SDK 方法注册器
     * @param feiApiClient      平台 SDK 客户端
     * @param responseValidator 探测响应契约校验器
     */
    public InterfacePublishProbeServiceImpl(SdkMethodRegistry sdkMethodRegistry,
                                            FeiApiClient feiApiClient,
                                            InterfaceProbeResponseValidator responseValidator) {
        this.sdkMethodRegistry = sdkMethodRegistry;
        this.feiApiClient = feiApiClient;
        this.responseValidator = responseValidator;
    }

    /**
     * 执行真实 SDK 发布探测并校验响应契约。
     *
     * @param publishContext 发布上下文
     */
    @Override
    public void probe(InterfacePublishContext publishContext) {
        Future<ProbeInvocationResult> future;
        try {
            future = probeExecutor.submit(() -> doProbe(publishContext));
        } catch (RuntimeException exception) {
            throw new InterfacePublishProbeException(PublishProbeFailureStageEnum.SDK_INVOCATION, "发布探测资源繁忙");
        }
        ProbeInvocationResult result;
        try {
            result = future.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new InterfacePublishProbeException(PublishProbeFailureStageEnum.TOTAL_TIMEOUT, "发布探测超过 15 秒");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterfacePublishProbeException(PublishProbeFailureStageEnum.SDK_INVOCATION, "发布探测被中断");
        } catch (ExecutionException exception) {
            throw classifyExecutionException(exception.getCause());
        }
        responseValidator.validate(publishContext, result);
    }

    /**
     * 在工作线程内执行 SDK 调用。
     *
     * @param publishContext 发布上下文
     * @return 探测响应元数据
     */
    private ProbeInvocationResult doProbe(InterfacePublishContext publishContext) {
        try {
            feiApiClient.enableProbeMode();
            sdkMethodRegistry.invoke(feiApiClient,
                    publishContext.getInterfaceInfo().getSdkMethodName(),
                    publishContext.getProbeRequestParams());
            return feiApiClient.getProbeInvocationResult();
        } finally {
            feiApiClient.disableProbeMode();
        }
    }

    /**
     * 将执行异常分类为发布探测异常。
     *
     * @param cause 原始异常
     * @return 发布探测异常
     */
    private InterfacePublishProbeException classifyExecutionException(Throwable cause) {
        if (cause instanceof InterfacePublishProbeException publishProbeException) {
            return publishProbeException;
        }
        if (hasCause(cause, ProbeResponseTooLargeException.class)) {
            return new InterfacePublishProbeException(PublishProbeFailureStageEnum.RESPONSE_FORMAT,
                    "响应体超过 1 MiB");
        }
        if (hasConnectionTimeoutCause(cause)) {
            return new InterfacePublishProbeException(PublishProbeFailureStageEnum.CONNECTION_TIMEOUT,
                    "连接网关或下游服务超时");
        }
        if (hasCause(cause, SocketTimeoutException.class)) {
            return new InterfacePublishProbeException(PublishProbeFailureStageEnum.RESPONSE_TIMEOUT,
                    "等待或读取响应超时");
        }
        return new InterfacePublishProbeException(PublishProbeFailureStageEnum.SDK_INVOCATION,
                "SDK 调用失败");
    }

    /**
     * 判断异常链中是否存在指定类型。
     *
     * @param throwable 原始异常
     * @param type      异常类型
     * @return 是否存在
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
     * 判断异常链是否表示连接阶段超时。
     *
     * <p>Hutool 底层使用 JDK HTTP 连接，连接超时与读取超时都可能表现为
     * {@link SocketTimeoutException}。JDK 连接超时消息固定包含 connect，
     * 因此结合异常类型和受控关键词区分两个阶段，原始消息不会向调用方返回。</p>
     *
     * @param throwable 原始异常
     * @return 是否为连接阶段超时
     */
    private boolean hasConnectionTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    && String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
