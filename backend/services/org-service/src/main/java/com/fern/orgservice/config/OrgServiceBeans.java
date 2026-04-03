package com.fern.orgservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.alerts.KafkaOperationalAlertPublisher;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class OrgServiceBeans {
    private static final String CALLER_SERVICE = "org-service";

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
    @Qualifier("posRestClient")
    RestClient posRestClient(@Qualifier("posClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @Qualifier("procurementRestClient")
    RestClient procurementRestClient(@Qualifier("procurementClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @Qualifier("financeRestClient")
    RestClient financeRestClient(@Qualifier("financeClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @Qualifier("posCircuitBreaker")
    CircuitBreaker posCircuitBreaker(@Qualifier("posClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    @Bean
    @Qualifier("procurementCircuitBreaker")
    CircuitBreaker procurementCircuitBreaker(@Qualifier("procurementClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    @Bean
    @Qualifier("financeCircuitBreaker")
    CircuitBreaker financeCircuitBreaker(@Qualifier("financeClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.clients")
    OrgClientProperties orgClientProperties() {
        return new OrgClientProperties();
    }

    @Bean
    @Qualifier("posClientSpec")
    FernDownstreamClientSpec posClientSpec(OrgClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "pos-service", "pos", properties.getPos());
    }

    @Bean
    @Qualifier("procurementClientSpec")
    FernDownstreamClientSpec procurementClientSpec(OrgClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "procurement-service", "procurement", properties.getProcurement());
    }

    @Bean
    @Qualifier("financeClientSpec")
    FernDownstreamClientSpec financeClientSpec(OrgClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "finance-service", "finance", properties.getFinance());
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
        return new KafkaOperationalAlertPublisher(kafkaTemplate, objectMapper, clock, "org-service");
    }
}
