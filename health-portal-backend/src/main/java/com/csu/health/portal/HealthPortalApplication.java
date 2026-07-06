package com.csu.health.portal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.csu.health.portal.module.**.mapper")
public class HealthPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthPortalApplication.class, args);
    }
}
