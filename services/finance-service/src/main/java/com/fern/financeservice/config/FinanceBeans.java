package com.fern.financeservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.SnowflakeIdGenerator;
import com.zaxxer.hikari.HikariDataSource;
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

@Configuration
public class FinanceBeans {
    @Value("${fern.datasource.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${fern.datasource.min-idle:1}")
    private int minIdle;

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
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
    @ConfigurationProperties("fern.projection-datasource")
    DataSourceProperties projectionDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource projectionDataSource(@Qualifier("projectionDataSourceProperties") DataSourceProperties projectionDataSourceProperties) {
        return tunePool(projectionDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    NamedParameterJdbcTemplate projectionJdbcTemplate(@Qualifier("projectionDataSource") DataSource projectionDataSource) {
        return new NamedParameterJdbcTemplate(projectionDataSource);
    }

    @Bean
    DataSourceTransactionManager projectionTransactionManager(@Qualifier("projectionDataSource") DataSource projectionDataSource) {
        return new DataSourceTransactionManager(projectionDataSource);
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
