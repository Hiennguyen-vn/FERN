package com.fern.financeservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fern.platform.alerts.KafkaOperationalAlertPublisher;
import com.fern.platform.alerts.NoopOperationalAlertPublisher;
import com.fern.platform.alerts.OperationalAlertPublisher;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.JdbcAuditOutboxEventPublisher;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.web.FernDownstreamClientFactory;
import com.fern.platform.web.FernDownstreamClientSpec;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class FinanceBeans {
    @Value("${fern.datasource.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${fern.datasource.min-idle:1}")
    private int minIdle;

    private static final String CALLER_SERVICE = "finance-service";

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
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        return new JdbcAuditOutboxEventPublisher(jdbcTemplate, objectMapper, "finance.outbox_event", true);
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
        return new KafkaOperationalAlertPublisher(kafkaTemplate, objectMapper, clock, "finance-service");
    }

    @Bean
    @Qualifier("hrRestClient")
    RestClient restClient(@Qualifier("hrClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @Qualifier("hrCircuitBreaker")
    CircuitBreaker hrCircuitBreaker(@Qualifier("hrClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.clients")
    FinanceClientProperties financeClientProperties() {
        return new FinanceClientProperties();
    }

    @Bean
    @Qualifier("hrClientSpec")
    FernDownstreamClientSpec hrClientSpec(FinanceClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "hr-service", "hr", properties.getHr());
    }

    @Bean
    SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${fern.id-generator.epoch-millis:1767225600000}") long epochMillis,
            @Value("${fern.id-generator.node-id:9}") long nodeId,
            @Value("${fern.id-generator.max-clock-rollback-millis:5}") long maxClockRollbackMillis
    ) {
        return new SnowflakeIdGenerator(epochMillis, nodeId, maxClockRollbackMillis);
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties operationalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    DataSource dataSource(@Qualifier("operationalDataSourceProperties") DataSourceProperties operationalDataSourceProperties) {
        return tunePool(operationalDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    @Primary
    NamedParameterJdbcTemplate operationalJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    DataSourceTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate transactionTemplate(@Qualifier("transactionManager") DataSourceTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConfigurationProperties("fern.master-datasource")
    DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("fern.projection-datasource")
    DataSourceProperties projectionDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties masterDataSourceProperties) {
        return tunePool(masterDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    DataSource projectionDataSource(@Qualifier("projectionDataSourceProperties") DataSourceProperties projectionDataSourceProperties) {
        return tunePool(projectionDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    NamedParameterJdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return new NamedParameterJdbcTemplate(masterDataSource);
    }

    @Bean
    NamedParameterJdbcTemplate projectionJdbcTemplate(@Qualifier("projectionDataSource") DataSource projectionDataSource) {
        return new NamedParameterJdbcTemplate(projectionDataSource);
    }

    @Bean
    DataSourceTransactionManager masterTransactionManager(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return new DataSourceTransactionManager(masterDataSource);
    }

    @Bean
    DataSourceTransactionManager projectionTransactionManager(@Qualifier("projectionDataSource") DataSource projectionDataSource) {
        return new DataSourceTransactionManager(projectionDataSource);
    }

    @Bean
    TransactionTemplate projectionTransactionTemplate(
            @Qualifier("projectionTransactionManager") DataSourceTransactionManager projectionTransactionManager
    ) {
        return new TransactionTemplate(projectionTransactionManager);
    }

    @Bean(initMethod = "migrate")
    Flyway financeOperationalFlyway(@Qualifier("dataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("finance")
                .defaultSchema("finance")
                .locations("classpath:db/migration/postgresql/operational")
                .load();
    }

    @Bean(initMethod = "migrate")
    Flyway financeConfigFlyway(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return Flyway.configure()
                .dataSource(masterDataSource)
                .schemas("config")
                .defaultSchema("config")
                .locations("classpath:db/migration/postgresql/master")
                .load();
    }

    @Bean(initMethod = "migrate")
    Flyway financeProjectionFlyway(@Qualifier("projectionDataSource") DataSource projectionDataSource) {
        return Flyway.configure()
                .dataSource(projectionDataSource)
                .schemas("finance_projection")
                .defaultSchema("finance_projection")
                .locations("classpath:db/migration/postgresql/master_projection")
                .load();
    }

    private DataSource tunePool(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.setMaximumPoolSize(maxPoolSize);
            hikariDataSource.setMinimumIdle(Math.min(minIdle, maxPoolSize));
        }
        return dataSource;
    }
}
