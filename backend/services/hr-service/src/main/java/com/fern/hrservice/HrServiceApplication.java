package com.fern.hrservice;

import com.fern.platform.security.FernJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FernJwtProperties.class)
public class HrServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HrServiceApplication.class, args);
    }
}
