package com.feiting.feiapiclientsdk;

import com.feiting.feiapiclientsdk.client.FeiApiClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("feiapi.client")
@Data
@ComponentScan
public class FeiapiClientConfig {

    private String accessKey;
    private String secretKey;
    private String gatewayHost;

    @Bean
    public FeiApiClient feiApiClient(){
        return new FeiApiClient(accessKey, secretKey, gatewayHost);
    }

}
