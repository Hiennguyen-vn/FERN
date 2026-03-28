package com.fern.posservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.KafkaOperationalAlertPublisher;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.JdbcAuditOutboxEventPublisher;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class PosBeans {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FernJwtService fernJwtService(FernJwtProperties properties, Clock clock) {
        return new FernJwtService(properties, clock);
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
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    AuditEventPublisher auditEventPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        return new JdbcAuditOutboxEventPublisher(jdbcTemplate, objectMapper, "pos.outbox_event", true);
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
        return new KafkaOperationalAlertPublisher(kafkaTemplate, objectMapper, clock, "pos-service");
    }

    @Bean
    @Qualifier("catalogRestClient")
    RestClient catalogRestClient(PosClientProperties properties) {
        return buildRestClient(properties.getCatalog());
    }

    @Bean
    @Qualifier("inventoryRestClient")
    RestClient inventoryRestClient(PosClientProperties properties) {
        return buildRestClient(properties.getInventory());
    }

    @Bean
    @Qualifier("orgRestClient")
    RestClient orgRestClient(PosClientProperties properties) {
        return buildRestClient(properties.getOrg());
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.clients")
    PosClientProperties posClientProperties() {
        return new PosClientProperties();
    }

    @Bean
    CircuitBreaker catalogCircuitBreaker(PosClientProperties properties) {
        return CircuitBreaker.of("catalog-client", toCircuitBreakerConfig(properties.getCatalog().getCircuitBreaker()));
    }

    @Bean
    CircuitBreaker inventoryCircuitBreaker(PosClientProperties properties) {
        return CircuitBreaker.of("inventory-client", toCircuitBreakerConfig(properties.getInventory().getCircuitBreaker()));
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.outbox")
    PosOutboxProperties posOutboxProperties() {
        return new PosOutboxProperties();
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    private RestClient buildRestClient(PosClientProperties.ClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private CircuitBreakerConfig toCircuitBreakerConfig(PosClientProperties.CircuitBreakerProperties properties) {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.getFailureRateThreshold())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .slidingWindowSize(properties.getSlidingWindowSize())
                .waitDurationInOpenState(properties.getWaitDurationInOpenState())
                .build();
    }
}
