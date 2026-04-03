package com.fern.iamservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fern.platform.alerts.KafkaOperationalAlertPublisher;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.JdbcAuditOutboxEventPublisher;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwksProvider;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.security.FernPasswordHasher;
import java.time.Clock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class IamBeans {
    private static final String CALLER_SERVICE = "iam-service";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FernJwtService fernJwtService(FernJwtProperties properties, Clock clock) {
        return new FernJwtService(properties, clock);
    }

    @Bean
    FernJwksProvider fernJwksProvider(FernJwtService jwtService) {
        return jwtService.jwksProvider();
    }

    @Bean
    FernServiceTokenSupport fernServiceTokenSupport(
            FernJwtService jwtService,
            Clock clock,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider
    ) {
        return new FernServiceTokenSupport(jwtService, clock, redisTemplateProvider.getIfAvailable());
    }

    @Bean
    FernPasswordHasher fernPasswordHasher() {
        return new FernPasswordHasher();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean
    AuditEventPublisher auditEventPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        return new JdbcAuditOutboxEventPublisher(jdbcTemplate, objectMapper, "iam.outbox_event", false);
    }

    @Bean
    OperationalAlertPublisher operationalAlertPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return new NoopOperationalAlertPublisher();
        }
        return new KafkaOperationalAlertPublisher(kafkaTemplate, objectMapper, clock, "iam-service");
    }

    @Bean
    @Qualifier("orgRestClient")
    RestClient orgRestClient(@Qualifier("orgClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @Qualifier("orgCircuitBreaker")
    CircuitBreaker orgCircuitBreaker(@Qualifier("orgClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.clients")
    IamClientProperties iamClientProperties() {
        return new IamClientProperties();
    }

    @Bean
    @Qualifier("orgClientSpec")
    FernDownstreamClientSpec orgClientSpec(IamClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "org-service", "org", properties.getOrg());
    }
}
