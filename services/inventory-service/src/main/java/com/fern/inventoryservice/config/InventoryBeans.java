package com.fern.inventoryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.KafkaOperationalAlertPublisher;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.JdbcAuditOutboxEventPublisher;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class InventoryBeans {
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
        return new JdbcAuditOutboxEventPublisher(jdbcTemplate, objectMapper, "inventory.outbox_event", true);
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
        return new KafkaOperationalAlertPublisher(kafkaTemplate, objectMapper, clock, "inventory-service");
    }

    @Bean
    @Qualifier("orgRestClient")
    RestClient orgRestClient(InventoryClientProperties properties) {
        return buildRestClient(properties.getOrg());
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.clients")
    InventoryClientProperties inventoryClientProperties() {
        return new InventoryClientProperties();
    }

    private RestClient buildRestClient(InventoryClientProperties.ClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
