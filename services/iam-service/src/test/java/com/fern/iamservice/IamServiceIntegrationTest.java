package com.fern.iamservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.iamservice.repository.AuthSessionRepository;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.testsupport.FernIntegrationContainers;
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

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("iam"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> false);
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

        MvcResult createUserResult = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + accessToken)
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
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCodes":["bootstrap_admin"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes[0]").value("bootstrap_admin"));

        mockMvc.perform(post("/users/%d/scopes".formatted(userId))
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[1],"outletIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeRoots.regions[0]").value(1));

        mockMvc.perform(put("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + accessToken)
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

        mockMvc.perform(get("/users/%d/permission-overrides".formatted(userId))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[?(@.permissionCode=='org.region.read')]").exists())
                .andExpect(jsonPath("$.overrides[?(@.permissionCode=='audit.read')]").exists());

        mockMvc.perform(get("/users/%d/effective-access".formatted(userId))
                        .header("Authorization", "Bearer " + accessToken))
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

        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);
        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(bootstrapAdminId)).hasSize(1);
        Long totalSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM iam.auth_session WHERE user_id = 1", Long.class);
        assertThat(totalSessions).isEqualTo(2);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + rotatedAccessToken)
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
    void shouldRejectManualLockedSuspendedAndInactiveStatuses() throws Exception {
        String adminToken = loginAsBootstrapAdmin().get("accessToken").asText();
        Long userId = createUser(adminToken, "status-user", "Status123!").get("id").asLong();

        for (String statusValue : java.util.List.of("LOCKED", "SUSPENDED", "INACTIVE")) {
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
        String adminToken = loginAsBootstrapAdmin().get("accessToken").asText();
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
        String adminToken = loginAsBootstrapAdmin().get("accessToken").asText();
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
        String adminToken = loginAsBootstrapAdmin().get("accessToken").asText();
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
        String adminToken = loginAsBootstrapAdmin().get("accessToken").asText();
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
        String adminToken = loginAsBootstrapAdmin().get("accessToken").asText();
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
}
