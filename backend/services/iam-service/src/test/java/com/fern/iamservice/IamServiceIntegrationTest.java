package com.fern.iamservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.iamservice.repository.AuthSessionRepository;
import com.fern.iamservice.client.OrgScopeExpansionClient;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.audit.SecurityEvent;
import com.fern.platform.common.FernPrincipalType;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.observability.CorrelationId;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class IamServiceIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private com.fern.iamservice.service.PolicyVersionService policyVersionService;

    @Autowired
    private com.fern.iamservice.service.ScopeVersionBridgeService scopeVersionBridgeService;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private OrgScopeExpansionClient orgScopeExpansionClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("iam"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> false);
        registry.add("fern.security.jwt.secret", () -> "XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> true);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM iam.auth_session");
        jdbcTemplate.update("DELETE FROM iam.user_permission_override");
        jdbcTemplate.update("DELETE FROM iam.user_scope_assignment WHERE user_id > 1");
        jdbcTemplate.update("DELETE FROM iam.user_role_assignment WHERE user_id > 1");
        jdbcTemplate.update("DELETE FROM iam.user_account WHERE id > 1");
        jdbcTemplate.update("DELETE FROM iam.outbox_event");
        jdbcTemplate.update("UPDATE iam.user_account SET status = 'ACTIVE' WHERE id = 1");
        redisTemplate.delete("fern:versions:policy");
        redisTemplate.delete("fern:versions:scope");
        redisTemplate.delete("fern:iam:login-fail:bootstrap-admin");
        redisTemplate.delete("fern:iam:login-lock:bootstrap-admin");
        reset(auditEventPublisher);
        when(orgScopeExpansionClient.expand(any())).thenAnswer(invocation -> {
            ScopeRoots roots = invocation.getArgument(0);
            return roots == null ? ScopeRoots.empty() : roots;
        });
    }

    @Test
    void shouldLoginCreateUserAssignAccessRotateSessionAndLogout() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bootstrap-admin","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();
        String refreshToken = loginJson.get("refreshToken").asText();
        Long bootstrapAdminId = loginJson.get("user").get("id").asLong();
        String adminToken = relayForIam(accessToken);

        MvcResult createUserResult = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"cashier-1",
                                  "password":"Cashier123!",
                                  "fullName":"Cashier One",
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("cashier-1"))
                .andReturn();

        Long userId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/users/%d/roles".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["bootstrap_admin"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes[0]").value("bootstrap_admin"));

        adminToken = issueBootstrapAdminToken();

        mockMvc.perform(post("/users/%d/scopes".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[1],"outletIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeRoots.regions[0]").value(1));

        adminToken = issueBootstrapAdminToken();

        mockMvc.perform(put("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "permissionCode": "org.region.read",
                                      "overrideMode": "DENY",
                                      "reason": "Temporarily deny region access"
                                    },
                                    {
                                      "permissionCode": "audit.read",
                                      "overrideMode": "GRANT",
                                      "reason": "Allow audit review"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[0].permissionCode").exists());

        adminToken = issueBootstrapAdminToken();

        mockMvc.perform(get("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[?(@.permissionCode=='org.region.read')]").exists())
                .andExpect(jsonPath("$.overrides[?(@.permissionCode=='audit.read')]").exists());

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("bootstrap_admin"))
                .andExpect(jsonPath("$.deniedPermissions[?(@=='org.region.read')]").exists())
                .andExpect(jsonPath("$.effectivePermissions[?(@=='org.region.read')]").doesNotExist())
                .andExpect(jsonPath("$.effectivePermissions[?(@=='audit.read')]").exists())
                .andExpect(jsonPath("$.scopeRoots.regions[0]").value(1));

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String rotatedAccessToken = refreshJson.get("accessToken").asText();
        String rotatedRefreshToken = refreshJson.get("refreshToken").asText();
        String rotatedRelayAccessToken = relayForIam(rotatedAccessToken);

        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);
        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(bootstrapAdminId)).hasSize(1);
        Long totalSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.auth_session WHERE user_id = 1", Long.class);
        assertThat(totalSessions).isEqualTo(2);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + rotatedRelayAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(rotatedRefreshToken)))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.keys("fern:iam:blacklist:*")).isNotEmpty();
        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(bootstrapAdminId)).isEmpty();

        verify(auditEventPublisher, atLeastOnce()).publishSecurityEvent(argThat(event ->
                event.eventType().equals("iam.auth.login.succeeded") || event.eventType().equals("iam.auth.logout")));
        verify(auditEventPublisher, atLeastOnce()).publishAuditEvent(argThat(event ->
                event.eventType().equals("iam.user.created")
                        || event.eventType().equals("iam.user.access_changed")
                        || event.eventType().equals("iam.user.permission_override.changed")));
    }

    @Test
    void shouldTemporarilyLockAfterFiveFailuresAndAllowAgainAfterLockCleared() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"bootstrap-admin","password":"WrongPassword!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bootstrap-admin","password":"Admin123!"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(redisTemplate.hasKey("fern:iam:login-lock:bootstrap-admin")).isTrue();

        redisTemplate.delete("fern:iam:login-lock:bootstrap-admin");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bootstrap-admin","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());

        verify(auditEventPublisher, atLeastOnce()).publishSecurityEvent(argThat(event ->
                event.eventType().equals("iam.auth.locked") || event.eventType().equals("iam.auth.login.failed")));
    }

    @Test
    void shouldBrowseUsersBySearchAndStatus() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        createUser(adminToken, "ops-reader", "OpsReader123!");

        adminToken = issueBootstrapAdminToken();
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"ops-locked",
                                  "password":"OpsLocked123!",
                                  "fullName":"Ops Locked",
                                  "status":"LOCKED"
                                }
                                """))
                .andExpect(status().isOk());

        adminToken = issueBootstrapAdminToken();

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "ops")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].username").value("ops-reader"))
                .andExpect(jsonPath("$.items[?(@.username=='ops-locked')]").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void shouldRejectManualLockedSuspendedAndInactiveStatuses() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "status-user", "Status123!").get("id").asLong();

        for (String statusValue : java.util.List.of("LOCKED", "SUSPENDED", "INACTIVE")) {
            adminToken = issueBootstrapAdminToken();
            mockMvc.perform(patch("/users/%d".formatted(userId))
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"%s"}
                                    """.formatted(statusValue)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(statusValue));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"status-user","password":"Status123!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void shouldRejectDuplicatePermissionOverridesBeforeMutation() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "override-user", "Override123!").get("id").asLong();

        mockMvc.perform(put("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "permissionCode": "org.region.read",
                                      "overrideMode": "DENY",
                                      "reason": "Keep the existing override"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        adminToken = issueBootstrapAdminToken();
        reset(auditEventPublisher);

        mockMvc.perform(put("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "permissionCode": "audit.read",
                                      "overrideMode": "GRANT",
                                      "reason": "First duplicate"
                                    },
                                    {
                                      "permissionCode": "audit.read",
                                      "overrideMode": "DENY",
                                      "reason": "Second duplicate"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Duplicate permissionCode in request: audit.read"));

        verifyNoInteractions(auditEventPublisher);

        mockMvc.perform(get("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides.length()").value(1))
                .andExpect(jsonPath("$.overrides[0].permissionCode").value("org.region.read"))
                .andExpect(jsonPath("$.overrides[0].overrideMode").value("DENY"));
    }

    @Test
    void shouldDifferentiateProfileStatusAndNoopUserUpdates() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "patch-user", "Patch123!").get("id").asLong();

        jdbcTemplate.update("DELETE FROM iam.outbox_event");
        redisTemplate.delete("fern:versions:policy");
        reset(auditEventPublisher);

        mockMvc.perform(patch("/users/%d".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Updated Patch User",
                                  "email": "patch-user@example.com",
                                  "phone": "0900000001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Patch User"))
                .andExpect(jsonPath("$.email").value("patch-user@example.com"))
                .andExpect(jsonPath("$.phone").value("0900000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(jdbcTemplate.queryForList("SELECT event_type FROM iam.outbox_event ORDER BY created_at", String.class))
                .containsExactly("iam.user.changed");
        assertThat(redisTemplate.hasKey("fern:versions:policy")).isFalse();

        ArgumentCaptor<AuditEvent> profileAuditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishAuditEvent(profileAuditCaptor.capture());
        assertThat(profileAuditCaptor.getValue().eventType()).isEqualTo("iam.user.changed");
        assertThat(profileAuditCaptor.getValue().action()).isEqualTo("UPDATE_USER");

        jdbcTemplate.update("DELETE FROM iam.outbox_event");
        redisTemplate.delete("fern:versions:policy");
        reset(auditEventPublisher);

        mockMvc.perform(patch("/users/%d".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(jdbcTemplate.queryForList("SELECT event_type FROM iam.outbox_event ORDER BY created_at", String.class))
                .containsExactly("iam.user.status-changed");
        assertThat(redisTemplate.hasKey("fern:versions:policy")).isTrue();

        ArgumentCaptor<AuditEvent> statusAuditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishAuditEvent(statusAuditCaptor.capture());
        assertThat(statusAuditCaptor.getValue().eventType()).isEqualTo("iam.user.status_changed");
        assertThat(statusAuditCaptor.getValue().action()).isEqualTo("UPDATE_USER_STATUS");

        jdbcTemplate.update("DELETE FROM iam.outbox_event");
        redisTemplate.delete("fern:versions:policy");
        reset(auditEventPublisher);
        adminToken = issueBootstrapAdminToken();

        mockMvc.perform(patch("/users/%d".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.outbox_event", Long.class)).isZero();
        assertThat(redisTemplate.hasKey("fern:versions:policy")).isFalse();
        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void shouldAssignSystemScopeAndExposeItInEffectiveAccess() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "system-scope-user", "Scope123!").get("id").asLong();

        mockMvc.perform(post("/users/%d/scopes".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "system": true,
                                  "regionIds": [1],
                                  "outletIds": [101]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeRoots.system").value(true))
                .andExpect(jsonPath("$.scopeRoots.regions[0]").value(1))
                .andExpect(jsonPath("$.scopeRoots.outlets[0]").value(101));

        adminToken = issueBootstrapAdminToken();

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeRoots.system").value(true));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM iam.user_scope_assignment
                WHERE user_id = ? AND scope_type = 'SYSTEM' AND scope_id IS NULL
                """, Long.class, userId)).isEqualTo(1L);
    }

    @Test
    void shouldBumpPolicyVersionWhenPermissionOverridesChange() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "policy-override-user", "Override123!").get("id").asLong();
        redisTemplate.delete("fern:versions:policy");

        mockMvc.perform(put("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "permissionCode": "audit.read",
                                      "overrideMode": "GRANT",
                                      "reason": "Temporary audit access"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey("fern:versions:policy")).isTrue();
    }

    @Test
    void shouldRevokeRefreshSessionsWhenUserIsDisabled() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        createUser(adminToken, "refresh-user", "Refresh123!");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"refresh-user","password":"Refresh123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        Long userId = loginJson.get("user").get("id").asLong();
        String refreshToken = loginJson.get("refreshToken").asText();

        mockMvc.perform(patch("/users/%d".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());

        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId)).isEmpty();
    }

    @Test
    void shouldRejectOldAccessTokenAfterRoleRevoked() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "role-revoke-user", "Role123!").get("id").asLong();

        mockMvc.perform(post("/users/%d/roles".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["bootstrap_admin"]}
                                """))
                .andExpect(status().isOk());

        String staleToken = relayForIam(login("role-revoke-user", "Role123!").get("accessToken").asText());

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePermissions").isArray());

        adminToken = issueBootstrapAdminToken();
        mockMvc.perform(post("/users/%d/roles".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":[]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldBumpPolicyVersionAtomicallyUnderConcurrency() throws Exception {
        int taskCount = 8;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        long initial = currentPolicyVersion();
        try {
            List<Future<Long>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return policyVersionService.bump();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Long> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(currentPolicyVersion()).isEqualTo(initial + taskCount);
        assertThat(redisTemplate.opsForValue().get("fern:versions:policy")).isEqualTo(Long.toString(initial + taskCount));
    }

    @Test
    void shouldBumpScopeVersionAtomicallyUnderConcurrency() throws Exception {
        int taskCount = 8;
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        long initial = currentScopeVersion();
        try {
            List<Future<Long>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return scopeVersionBridgeService.bump();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Long> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(currentScopeVersion()).isEqualTo(initial + taskCount);
        assertThat(redisTemplate.opsForValue().get("fern:versions:scope")).isEqualTo(Long.toString(initial + taskCount));
    }

    @Test
    void shouldRejectOldAccessTokenAfterScopeChanged() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "scope-revoke-user", "Scope123!").get("id").asLong();

        mockMvc.perform(post("/users/%d/roles".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["bootstrap_admin"]}
                                """))
                .andExpect(status().isOk());

        adminToken = issueBootstrapAdminToken();
        mockMvc.perform(post("/users/%d/scopes".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[1],"outletIds":[101]}
                                """))
                .andExpect(status().isOk());

        String staleToken = relayForIam(login("scope-revoke-user", "Scope123!").get("accessToken").asText());

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeRoots.regions[0]").value(1));

        adminToken = issueBootstrapAdminToken();
        mockMvc.perform(post("/users/%d/scopes".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[2],"outletIds":[201]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIgnoreExpiredPermissionOverrideInEffectiveAccess() throws Exception {
        String adminToken = issueBootstrapAdminToken();
        Long userId = createUser(adminToken, "expired-override-user", "Override123!").get("id").asLong();

        mockMvc.perform(put("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "permissionCode": "audit.read",
                                      "overrideMode": "GRANT",
                                      "reason": "Emergency access already expired",
                                      "expiresAt": "2020-01-01T00:00:00Z"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[0].permissionCode").value("audit.read"));

        adminToken = issueBootstrapAdminToken();
        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectivePermissions[?(@=='audit.read')]").doesNotExist())
                .andExpect(jsonPath("$.grantedPermissions[?(@=='audit.read')]").doesNotExist());
    }

    @Test
    void shouldPublishLogoutSecurityEventWithCorrelationAndClientMetadata() throws Exception {
        JsonNode login = loginAsBootstrapAdmin();
        String accessToken = relayForIam(login.get("accessToken").asText());
        String refreshToken = login.get("refreshToken").asText();
        reset(auditEventPublisher);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .header(CorrelationId.HEADER, "corr-logout-audit")
                        .header("User-Agent", "qa-smoke-suite/1.0")
                        .with(request -> {
                            request.setRemoteAddr("10.20.30.40");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<SecurityEvent> securityCaptor = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(auditEventPublisher).publishSecurityEvent(securityCaptor.capture());
        SecurityEvent event = securityCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("iam.auth.logout");
        assertThat(event.correlationId()).isEqualTo("corr-logout-audit");
        assertThat(event.ipAddress()).isEqualTo("10.20.30.40");
        assertThat(event.userAgent()).isEqualTo("qa-smoke-suite/1.0");
        assertThat(event.payload()).containsEntry("username", "bootstrap-admin");
    }

    private JsonNode loginAsBootstrapAdmin() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bootstrap-admin","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString());
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString());
    }

    private JsonNode createUser(String adminToken, String username, String password) throws Exception {
        MvcResult createUserResult = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"%s",
                                  "fullName":"%s",
                                  "status":"ACTIVE"
                                }
                                """.formatted(username, password, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(createUserResult.getResponse().getContentAsString());
    }

    private String issueBootstrapAdminToken() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        long policyVersion = currentPolicyVersion();
        long scopeVersion = currentScopeVersion();
        return jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                bootstrapAdminPermissions(),
                new ScopeRoots(true, List.of(1L), List.of()),
                policyVersion,
                scopeVersion,
                "iam-test-bootstrap-" + policyVersion + "-" + scopeVersion + "-" + Instant.now().toEpochMilli(),
                Instant.now(),
                Instant.now().plusSeconds(900),
                FernPrincipalType.USER,
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("iam-service")
        ), jwtService.accessTokenTtl());
    }

    private String relayForIam(String publicAccessToken) {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        FernJwtClaims claims = jwtService.decode(publicAccessToken);
        return jwtService.encode(new FernJwtClaims(
                claims.userId(),
                claims.username(),
                claims.roles(),
                claims.permissions(),
                claims.scopeRoots(),
                claims.accessibleScope(),
                claims.policyVersion(),
                claims.scopeVersion(),
                claims.jti(),
                claims.authTime(),
                claims.expiresAt(),
                claims.principalType(),
                FernJwtProperties.DEFAULT_GATEWAY_RELAY_USER_ISSUER,
                Set.of("iam-service")
        ), jwtService.accessTokenTtl());
    }

    private Set<String> bootstrapAdminPermissions() {
        return new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT permission.code
                FROM iam.role_permission role_permission
                JOIN iam.role role ON role.id = role_permission.role_id
                JOIN iam.permission permission ON permission.id = role_permission.permission_id
                WHERE role.code = 'bootstrap_admin'
                ORDER BY permission.code
                """, String.class));
    }

    private long currentPolicyVersion() {
        return jdbcTemplate.queryForObject("SELECT version FROM iam.policy_version_state WHERE id = 1", Long.class);
    }

    private long currentScopeVersion() {
        return jdbcTemplate.queryForObject("SELECT version FROM iam.scope_version_state WHERE id = 1", Long.class);
    }
}
