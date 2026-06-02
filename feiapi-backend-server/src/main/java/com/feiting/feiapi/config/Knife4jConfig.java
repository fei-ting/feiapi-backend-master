package com.feiting.feiapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Knife4j 接口文档配置
 * https://doc.xiaominfo.com/knife4j/documentation/get_start.html
 *
 * @author yupi
 */
@Configuration
@Profile("dev")
public class Knife4jConfig {

    @Bean
    public OpenAPI defaultOpenApi() {
        return new OpenAPI().info(new Info()
                .title("feiapi-backend")
                .description("feiapi-backend")
                .version("1.0"));
    }
}
