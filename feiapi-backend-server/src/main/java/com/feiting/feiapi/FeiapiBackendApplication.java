package com.feiting.feiapi;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
@MapperScan({
        "com.feiting.feiapi.mapper",
        "com.feiting.feiapi.interfaceplatform.documentation.mapper"
})
public class FeiapiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeiapiBackendApplication.class, args);
    }
}
