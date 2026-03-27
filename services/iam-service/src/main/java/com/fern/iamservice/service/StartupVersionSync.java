package com.fern.iamservice.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StartupVersionSync {
    @Bean
    ApplicationRunner syncPolicyAndScopeVersions(PolicyVersionService policyVersionService, ScopeVersionBridgeService scopeVersionBridgeService) {
        return args -> {
            policyVersionService.ensureRedisMirror();
            scopeVersionBridgeService.ensureRedisMirror();
        };
    }
}
