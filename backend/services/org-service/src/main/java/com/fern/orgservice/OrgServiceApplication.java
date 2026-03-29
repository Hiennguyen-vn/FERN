package com.fern.orgservice;

import com.fern.platform.security.FernJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(FernJwtProperties.class)
public class OrgServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrgServiceApplication.class, args);
    }
}
