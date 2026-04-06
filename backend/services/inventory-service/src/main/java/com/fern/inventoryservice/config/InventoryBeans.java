package com.fern.inventoryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fern.platform.alerts.KafkaOperationalAlertPublisher;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.JdbcAuditOutboxEventPublisher;
import com.fern.platform.common.OperationalShardAccess;
import com.fern.platform.common.OperationalShardRegistry;
import com.fern.platform.common.ShardId;
import com.fern.platform.common.ShardResolver;
import com.fern.platform.common.SingleOperationalShardRegistry;
import com.fern.platform.common.SingleShardResolver;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import java.time.Clock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import com.fern.platform.security.RedisSchedulerLock;

@Configuration
public class InventoryBeans {
    private static final String CALLER_SERVICE = "inventory-service";

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
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
    InventoryClientProperties inventoryClientProperties() {
        return new InventoryClientProperties();
    }

    @Bean
    @Qualifier("orgClientSpec")
    FernDownstreamClientSpec orgClientSpec(InventoryClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "org-service", "org", properties.getOrg());
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    ShardId defaultOperationalShardId(@Value("${fern.sharding.operational.default-shard:operational-0}") String value) {
        return new ShardId(value);
    }

    @Bean
    ShardResolver shardResolver(ShardId defaultOperationalShardId) {
        return new SingleShardResolver(defaultOperationalShardId);
    }

    @Bean
    OperationalShardRegistry operationalShardRegistry(
            ShardId defaultOperationalShardId,
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate
    ) {
        return new SingleOperationalShardRegistry(new OperationalShardAccess(defaultOperationalShardId, jdbcTemplate, transactionTemplate));
    }

    /**
     * P1-03 FIX: Distributed lock for scheduled reservation cleanup.
     * Returns null when Redis is unavailable — StockReservationService will
     * fall through to execute without the lock (same pattern as POS).
     */
    @Bean
    @org.springframework.lang.Nullable
    RedisSchedulerLock redisSchedulerLock(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return null;
        }
        String instanceId = System.getenv().getOrDefault("HOSTNAME", java.util.UUID.randomUUID().toString());
        return new RedisSchedulerLock(redis, instanceId);
    }
}
