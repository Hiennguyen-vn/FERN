package com.fern.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.KafkaAuditEventPublisher;
import com.fern.platform.audit.NoopAuditEventPublisher;
import com.fern.platform.observability.ReactiveCorrelationIdWebFilter;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.WebFilter;

@Configuration
public class GatewayBeans {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FernJwtService fernJwtService(FernJwtProperties properties, Clock clock) {
        return new FernJwtService(properties, clock);
    }

    @Bean
    WebFilter correlationIdWebFilter() {
        return new ReactiveCorrelationIdWebFilter();
    }

    @Bean
    AuditEventPublisher auditEventPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper
    ) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return new NoopAuditEventPublisher();
        }
        return new KafkaAuditEventPublisher(kafkaTemplate, objectMapper);
    }

    @Bean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate(org.springframework.data.redis.connection.ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
