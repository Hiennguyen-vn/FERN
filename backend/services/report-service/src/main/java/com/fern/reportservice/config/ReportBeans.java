package com.fern.reportservice.config;

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
import com.fern.platform.security.FernJwtService;
import com.fern.reportservice.service.ExportArtifactStorage;
import com.fern.reportservice.service.FileSystemExportArtifactStorage;
import com.fern.reportservice.service.S3ExportArtifactStorage;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.time.Clock;
import java.util.Objects;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fern.platform.security.FernServiceTokenSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class ReportBeans {
    private static final String CALLER_SERVICE = "report-service";

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
    OperationalAlertPublisher operationalAlertPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return new NoopOperationalAlertPublisher();
        }
        return new KafkaOperationalAlertPublisher(kafkaTemplate, objectMapper, clock, "report-service");
    }

    @Bean
    AuditEventPublisher auditEventPublisher(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        return new JdbcAuditOutboxEventPublisher(jdbcTemplate, objectMapper, "report.outbox_event", true);
    }

    @Bean
    SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${fern.id-generator.epoch-millis:1767225600000}") long epochMillis,
            @Value("${fern.id-generator.node-id:10}") long nodeId,
            @Value("${fern.id-generator.max-clock-rollback-millis:5}") long maxClockRollbackMillis
    ) {
        return new SnowflakeIdGenerator(epochMillis, nodeId, maxClockRollbackMillis);
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties landingDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    DataSource dataSource(DataSourceProperties landingDataSourceProperties) {
        return tunePool(landingDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    @Primary
    NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    @ConfigurationProperties("fern.projection-datasource")
    DataSourceProperties projectionDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource projectionDataSource(
            @org.springframework.beans.factory.annotation.Qualifier("projectionDataSourceProperties")
            DataSourceProperties projectionDataSourceProperties,
            @org.springframework.beans.factory.annotation.Qualifier("dataSource")
            DataSource landingDataSource
    ) {
        if (!hasText(projectionDataSourceProperties.getUrl())) {
            return landingDataSource;
        }
        return tunePool(projectionDataSourceProperties.initializeDataSourceBuilder().build());
    }

    @Bean
    NamedParameterJdbcTemplate projectionJdbcTemplate(@org.springframework.beans.factory.annotation.Qualifier("projectionDataSource") DataSource projectionDataSource) {
        return new NamedParameterJdbcTemplate(projectionDataSource);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    @ConfigurationProperties("fern.report.export")
    ReportExportProperties reportExportProperties() {
        return new ReportExportProperties();
    }

    @Bean
    ExportArtifactStorage exportArtifactStorage(
            ReportExportProperties exportProperties,
            ObjectProvider<S3Client> s3ClientProvider
    ) {
        if (exportProperties.usesS3()) {
            return new S3ExportArtifactStorage(
                    Objects.requireNonNull(s3ClientProvider.getIfAvailable(), "S3 client must be configured for S3 export storage"),
                    required(exportProperties.getBucket(), "fern.report.export.bucket"),
                    exportProperties.getTempDir()
            );
        }
        return new FileSystemExportArtifactStorage();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${fern.report.export.storage-backend:FILESYSTEM}'.equalsIgnoreCase('s3')")
    S3Client reportS3Client(ReportExportProperties exportProperties) {
        var builder = S3Client.builder()
                .region(Region.of(exportProperties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(exportProperties.isPathStyleAccessEnabled())
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (exportProperties.getEndpoint() != null && !exportProperties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(exportProperties.getEndpoint()));
        }
        if (hasText(exportProperties.getAccessKey()) || hasText(exportProperties.getSecretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    required(exportProperties.getAccessKey(), "fern.report.export.access-key"),
                    required(exportProperties.getSecretKey(), "fern.report.export.secret-key")
            )));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean(initMethod = "migrate")
    Flyway reportFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("raw_events", "report")
                .defaultSchema("raw_events")
                .locations("classpath:db/migration/postgresql/master")
                .load();
    }

    @Bean
    @ConfigurationProperties(prefix = "fern.clients")
    ReportClientProperties reportClientProperties() {
        return new ReportClientProperties();
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("reportPosClientSpec")
    FernDownstreamClientSpec reportPosClientSpec(ReportClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "pos-service", "pos", properties.getPos());
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("reportInventoryClientSpec")
    FernDownstreamClientSpec reportInventoryClientSpec(ReportClientProperties properties) {
        return new FernDownstreamClientSpec(CALLER_SERVICE, "inventory-service", "inventory", properties.getInventory());
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("reportPosRestClient")
    RestClient reportPosRestClient(@org.springframework.beans.factory.annotation.Qualifier("reportPosClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("reportInventoryRestClient")
    RestClient reportInventoryRestClient(@org.springframework.beans.factory.annotation.Qualifier("reportInventoryClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createRestClient(spec);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("reportPosCircuitBreaker")
    CircuitBreaker reportPosCircuitBreaker(@org.springframework.beans.factory.annotation.Qualifier("reportPosClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    @Bean
    @org.springframework.beans.factory.annotation.Qualifier("reportInventoryCircuitBreaker")
    CircuitBreaker reportInventoryCircuitBreaker(@org.springframework.beans.factory.annotation.Qualifier("reportInventoryClientSpec") FernDownstreamClientSpec spec, FernDownstreamClientFactory factory) {
        return factory.createCircuitBreaker(spec);
    }

    private DataSource tunePool(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.setMaximumPoolSize(maxPoolSize);
            hikariDataSource.setMinimumIdle(Math.min(minIdle, maxPoolSize));
        }
        return dataSource;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String required(String value, String propertyName) {
        if (!hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value;
    }
}
