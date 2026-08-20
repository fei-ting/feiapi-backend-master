package com.feiting.feiapi.interfaceplatform.publishing.component;

import com.feiting.feiapi.interfaceplatform.publishing.exception.InterfacePublishProbeException;
import com.feiting.feiapi.interfaceplatform.publishing.model.enums.PublishProbeFailureStageEnum;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapiclientsdk.FeiapiClientProperties;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 接口发布探测客户端工厂。
 *
 * <p>每次发布都读取当前管理员的最新调用凭证并创建独立客户端，避免用户密钥进入环境变量
 * 或被跨请求共享。</p>
 */
@Component
public class InterfaceProbeClientFactory {

    /**
     * 用户服务。
     */
    private final UserService userService;

    /**
     * SDK 网关和内部探测配置。
     */
    private final FeiapiClientProperties clientProperties;

    /**
     * 创建发布探测客户端工厂。
     *
     * @param userService      用户服务
     * @param clientProperties SDK 客户端配置
     */
    public InterfaceProbeClientFactory(UserService userService, FeiapiClientProperties clientProperties) {
        this.userService = userService;
        this.clientProperties = clientProperties;
    }

    /**
     * 创建当前管理员本次发布专用的 SDK 客户端。
     *
     * @param operatorId 当前登录管理员 ID
     * @return 发布探测 SDK 客户端
     */
    public FeiApiClient create(Long operatorId) {
        if (operatorId == null || operatorId <= 0) {
            throw probeCredentialException("发布操作缺少有效管理员身份");
        }
        User operator = userService.getById(operatorId);
        if (operator == null || !userService.isAdmin(operator)) {
            throw probeCredentialException("当前用户不是有效管理员");
        }
        if (StringUtils.isAnyBlank(operator.getAccessKey(), operator.getSecretKey())) {
            throw probeCredentialException("当前管理员调用凭证不可用");
        }
        if (StringUtils.isBlank(clientProperties.getProbeSecret())) {
            throw probeCredentialException("内部发布探测密钥未配置");
        }
        return new FeiApiClient(operator.getAccessKey(), operator.getSecretKey(),
                clientProperties.getGatewayHost(), clientProperties.getProbeSecret());
    }

    /**
     * 创建不暴露凭证内容的发布探测异常。
     *
     * @param reason 失败原因
     * @return 发布探测异常
     */
    private InterfacePublishProbeException probeCredentialException(String reason) {
        return new InterfacePublishProbeException(PublishProbeFailureStageEnum.SDK_INVOCATION, reason);
    }
}
