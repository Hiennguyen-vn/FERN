package com.fern.hrservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.JdbcAuditOutboxEventPublisher;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernServiceTokenSupport;
import com.fern.platform.security.FernJwtService;
import com.zaxxer.hikari.HikariDataSource;
import java.time.ZoneId;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

@Configuration
public class HrBeans {
    @Value("${fern.datasource.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${fern.datasource.min-idle:1}")
    private int minIdle;

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ZoneId attendanceBusinessZone(@Value("${fern.attendance.business-zone:Asia/Ho_Chi_Minh}") String zoneId) {
        return ZoneId.of(zoneId);
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
            @Qualifier("operationalJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        return new JdbcAuditOutboxEventPublisher(jdbcTemplate, objectMapper, "hr.outbox_event", true);
    }

    @Bean
    @Qualifier("orgRestClient")
    RestClient orgRestClient(@Value("${fern.clients.org.base-url:http://localhost:8082}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .build();
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
    @ConfigurationProperties("fern.master-datasource")
    DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties masterDataSourceProperties) {
        return tunePool(masterDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    NamedParameterJdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return new NamedParameterJdbcTemplate(masterDataSource);
    }

    @Bean
    DataSourceTransactionManager masterTransactionManager(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return new DataSourceTransactionManager(masterDataSource);
    }

    @Bean(initMethod = "migrate")
    Flyway hrOperationalFlyway(@Qualifier("dataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("hr")
                .defaultSchema("hr")
                .locations("classpath:db/migration/postgresql/operational")
                .load();
    }

    @Bean(initMethod = "migrate")
    Flyway hrMasterFlyway(@Qualifier("masterDataSource") DataSource masterDataSource) {
        return Flyway.configure()
                .dataSource(masterDataSource)
                .schemas("hr_master")
                .defaultSchema("hr_master")
                .locations("classpath:db/migration/postgresql/master")
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
