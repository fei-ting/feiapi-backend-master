package com.feiting.feiapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员一次性初始化配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feiapi.admin-bootstrap")
public class AdminBootstrapProperties {

    /**
     * 是否启用管理员一次性初始化任务。
     */
    private boolean enabled;

    /**
     * 管理员登录账号。
     */
    private String account;

    /**
     * 管理员初始密码。
     */
    private String initialPassword;

    /**
     * 管理员显示名称。
     */
    private String displayName = "管理员";

    /**
     * 管理员 AccessKey。
     */
    private String accessKey;

    /**
     * 管理员 SecretKey。
     */
    private String secretKey;
}
