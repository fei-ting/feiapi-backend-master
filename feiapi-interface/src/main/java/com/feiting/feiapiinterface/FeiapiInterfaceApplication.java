package com.feiting.feiapiinterface;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.feiting.feiapiinterface.mapper")
public class FeiapiInterfaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeiapiInterfaceApplication.class, args);
    }

}
