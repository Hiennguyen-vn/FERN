package com.fern.auditservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AuditServiceBeans {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FernJwtService fernJwtService(FernJwtProperties properties, Clock clock) {
        return new FernJwtService(properties, clock);
    }

    @Bean
    SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${fern.id-generator.node-id:4}") long nodeId,
            @Value("${fern.id-generator.epoch-millis:1767225600000}") long epochMillis,
            @Value("${fern.id-generator.max-clock-rollback-millis:5}") long maxClockRollbackMillis
    ) {
        return new SnowflakeIdGenerator(epochMillis, nodeId, maxClockRollbackMillis);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
