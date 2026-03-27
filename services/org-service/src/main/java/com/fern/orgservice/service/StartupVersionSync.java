package com.fern.orgservice.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupVersionSync {
    @Bean
    ApplicationRunner syncScopeVersionRunner(ScopeVersionService scopeVersionService) {
        return args -> scopeVersionService.ensureRedisMirror();
    }
}
