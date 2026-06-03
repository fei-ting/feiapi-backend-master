package com.feiting.feiapiclientsdk;

import com.feiting.feiapiclientsdk.client.FeiApiClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * FeiAPI 客户端自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(FeiapiClientProperties.class)
public class FeiapiClientConfig {

    @Bean
    public FeiApiClient feiApiClient(FeiapiClientProperties properties) {
        return new FeiApiClient(
                properties.getAccessKey(),
                properties.getSecretKey(),
                properties.getGatewayHost(),
                properties.getProbeSecret()
        );
    }
}
