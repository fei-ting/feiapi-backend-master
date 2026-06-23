package com.feiting.feiapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口真实目标地址安全配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feiapi.interface-target")
public class InterfaceTargetHostProperties {

    /**
     * 允许配置为 targetHost 的主机名白名单。
     */
    private List<String> allowedHostnames = new ArrayList<>(List.of("feiapi-interface"));
}
