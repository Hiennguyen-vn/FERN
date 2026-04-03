package com.fern.auditservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class AuditServiceBeans {
    @Value("${fern.datasource.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${fern.datasource.min-idle:1}")
    private int minIdle;

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
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Primary datasource (write path — Kafka ingestion) ──────────────────

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties writeDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    DataSource dataSource(@Qualifier("writeDataSourceProperties") DataSourceProperties writeDataSourceProperties) {
        return tunePool(writeDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    @Primary
    @Qualifier("writeJdbcTemplate")
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    DataSourceTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    // ── Read-replica datasource (query path — API reads) ───────────────────

    @Bean
    @ConfigurationProperties("fern.read-datasource")
    DataSourceProperties readDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource readDataSource(
            @Qualifier("readDataSourceProperties") DataSourceProperties readDataSourceProperties,
            @Qualifier("dataSource") DataSource primaryDataSource
    ) {
        if (readDataSourceProperties.getUrl() == null || readDataSourceProperties.getUrl().isBlank()) {
            return primaryDataSource;
        }
        return tunePool(readDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    @Qualifier("readJdbcTemplate")
    NamedParameterJdbcTemplate readJdbcTemplate(@Qualifier("readDataSource") DataSource readDataSource) {
        return new NamedParameterJdbcTemplate(readDataSource);
    }

    // ── Flyway ─────────────────────────────────────────────────────────────

    @Bean(initMethod = "migrate")
    Flyway auditFlyway(@Qualifier("dataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("audit")
                .defaultSchema("audit")
                .locations("classpath:db/migration/postgresql/reporting")
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
