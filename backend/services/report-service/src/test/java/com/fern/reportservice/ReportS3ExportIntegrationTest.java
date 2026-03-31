package com.fern.reportservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.contracts.ExpensePostedEvent;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import com.fern.reportservice.service.ExportArtifactStore;
import com.fern.reportservice.service.ReportService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.S3Configuration;

@SpringBootTest
@AutoConfigureMockMvc
class ReportS3ExportIntegrationTest {
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET = "fern-report-integration";
    private static final String KEY_PREFIX = "integration/exports";
    private static final Path STAGING_DIR = createTempDir("fern-report-s3-staging");
    private static final AtomicLong TOKEN_SEQUENCE = new AtomicLong();
    private static final AtomicBoolean BUCKET_READY = new AtomicBoolean();
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data", "--address", ":9000", "--console-address", ":9001")
            .withExposedPorts(9000);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ensureBucketExists();
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("report"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.autoconfigure.exclude", () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
        registry.add("fern.report.export.storage-backend", () -> "S3");
        registry.add("fern.report.export.bucket", () -> BUCKET);
        registry.add("fern.report.export.key-prefix", () -> KEY_PREFIX);
        registry.add("fern.report.export.region", () -> "us-east-1");
        registry.add("fern.report.export.endpoint", ReportS3ExportIntegrationTest::minioEndpoint);
        registry.add("fern.report.export.access-key", () -> ACCESS_KEY);
        registry.add("fern.report.export.secret-key", () -> SECRET_KEY);
        registry.add("fern.report.export.path-style-access-enabled", () -> "true");
        registry.add("fern.report.export.temp-dir", () -> STAGING_DIR.toString());
        registry.add("fern.report.export.preview-row-limit", () -> "5");
        registry.add("fern.report.export.worker-delay-ms", () -> "60000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ExportArtifactStore exportArtifactStore;

    @Autowired
    private S3Client s3Client;

    @BeforeEach
    void setUp() throws IOException {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    report.outbox_event,
                    report.company_daily_outlet,
                    report.region_daily_event,
                    report.export_job,
                    report.company_daily_summary,
                    report.region_daily_summary,
                    report.expense_fact,
                    report.payroll_fact,
                    report.attendance_fact,
                    report.procurement_fact,
                    report.inventory_movement_fact,
                    report.payment_fact,
                    report.sales_fact,
                    raw_events.event_landing
                RESTART IDENTITY CASCADE
                """);
        clearBucket();
        try (var stream = Files.list(STAGING_DIR)) {
            stream.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Unable to clean export staging artifact " + path, exception);
                }
            });
        }
    }

    @Test
    void shouldUploadDownloadAndDeleteExportArtifactsViaS3Backend() throws Exception {
        ExpensePostedEvent expense = expenseEvent("expense-event-s3-export", "expense-idem-s3-export", 9300L, new BigDecimal("123.45"));
        reportService.ingestExpensePosted(objectMapper.writeValueAsString(expense), expense);

        String response = mockMvc.perform(post("/reports/exports")
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false))
                        .header("Idempotency-Key", "export-s3-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "dataset": "EXPENSE_FACT",
                                  "format": "CSV",
                                  "regionId": 1,
                                  "fromDate": "2026-03-27",
                                  "toDate": "2026-03-27",
                                  "limit": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(response).get("exportJobId").asLong();

        reportService.processQueuedExports();

        String artifactPath = jdbcTemplate.queryForObject("""
                SELECT file_path
                FROM report.export_job
                WHERE export_job_id = ?
                """, String.class, jobId);
        assertThat(artifactPath).startsWith("s3://" + BUCKET + "/" + KEY_PREFIX + "/");
        assertThat(listObjectKeys()).hasSize(1);
        assertThat(listObjectKeys().getFirst()).isEqualTo(objectKey(artifactPath));

        String csv = mockMvc.perform(get("/reports/exports/{jobId}/download", jobId)
                        .header("Authorization", bearer(reportPermissions(), List.of(1L), false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("expense_record_id");
        assertThat(csv).contains("9300");
        assertThat(csv).contains("123.45");

        exportArtifactStore.deleteQuietly(artifactPath);

        assertThat(listObjectKeys()).isEmpty();
    }

    private ExpensePostedEvent expenseEvent(String eventId, String idempotencyKey, Long expenseRecordId, BigDecimal amount) {
        return new ExpensePostedEvent(
                eventId,
                "finance.expense.posted",
                Instant.parse("2026-03-27T08:00:00Z"),
                "finance-service",
                "corr-" + eventId,
                idempotencyKey,
                expenseRecordId,
                1L,
                101L,
                501L,
                7001L,
                LocalDate.parse("2026-03-27"),
                "PAYROLL",
                amount,
                "PAYROLL_RUN",
                "7001"
        );
    }

    private Set<String> reportPermissions() {
        return Set.of(PermissionCodes.REPORT_READ, PermissionCodes.REPORT_EXPORT);
    }

    private String bearer(Set<String> permissions, List<Long> regionIds, boolean systemScoped) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        long sequence = TOKEN_SEQUENCE.incrementAndGet();
        return "Bearer " + jwtService.encode(new FernJwtClaims(
                1000L + sequence,
                "report-s3-tester-" + sequence,
                Set.of("finance"),
                permissions,
                new ScopeRoots(systemScoped, regionIds, List.of()),
                1L,
                1L,
                "report-s3-test-jti-" + sequence,
                Instant.now(),
                Instant.now().plusSeconds(900),
                com.fern.platform.common.FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("report-service")
        ), jwtService.accessTokenTtl());
    }

    private List<String> listObjectKeys() {
        return s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(BUCKET)
                        .prefix(KEY_PREFIX + "/")
                        .build())
                .contents()
                .stream()
                .map(item -> item.key())
                .toList();
    }

    private void clearBucket() {
        for (String key : listObjectKeys()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(key)
                    .build());
        }
    }

    private static synchronized void ensureBucketExists() {
        if (!MINIO.isRunning()) {
            MINIO.start();
        }
        if (BUCKET_READY.compareAndSet(false, true)) {
            try (S3Client client = adminS3Client()) {
                boolean exists = client.listBuckets().buckets().stream().anyMatch(bucket -> BUCKET.equals(bucket.name()));
                if (!exists) {
                    client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
                }
            }
        }
    }

    private static S3Client adminS3Client() {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(minioEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String objectKey(String artifactPath) {
        URI uri = URI.create(artifactPath);
        return uri.getPath().substring(1);
    }

    private static Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create temp directory for report S3 tests", exception);
        }
    }
}
