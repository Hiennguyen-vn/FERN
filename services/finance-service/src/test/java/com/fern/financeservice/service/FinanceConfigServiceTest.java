package com.fern.financeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.financeservice.dto.FinanceCommands.PutNumberingRuleRequest;
import com.fern.financeservice.dto.FinanceCommands.PutSystemPolicyRequest;
import com.fern.financeservice.dto.FinanceResponses.NumberingRuleResponse;
import com.fern.financeservice.dto.FinanceResponses.SystemPolicyResponse;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class FinanceConfigServiceTest {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.master-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.master-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.master-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("fern.projection-datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("public"));
        registry.add("fern.projection-datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("fern.projection-datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("fern.outbox.enabled", () -> "false");
        registry.add("fern.security.jwt.secret", () -> "finance-config-service-test-secret-01234567890123456789");
        registry.add("fern.security.jwt.allow-insecure-default-secret", () -> "true");
    }

    @Autowired
    private FinanceConfigService financeConfigService;

    @Autowired
    @Qualifier("masterJdbcTemplate")
    private NamedParameterJdbcTemplate masterJdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @BeforeEach
    void setUp() {
        masterJdbcTemplate.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    config.document_numbering_rule,
                    config.system_policy
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void shouldPutAndGetNumberingRule() {
        NumberingRuleResponse response = financeConfigService.putNumberingRule(
                systemPrincipal(),
                "PAYROLL_RUN",
                new PutNumberingRuleRequest("RUN", 10L, null, 7L, "NEVER", null, true)
        );

        assertThat(response.documentType()).isEqualTo("PAYROLL_RUN");
        assertThat(response.prefix()).isEqualTo("RUN");
        assertThat(response.regionId()).isEqualTo(10L);
        assertThat(response.nextNumber()).isEqualTo(7L);
        assertThat(financeConfigService.getNumberingRule(systemPrincipal(), "PAYROLL_RUN").prefix()).isEqualTo("RUN");
    }

    @Test
    void shouldPutAndGetSystemPolicy() throws Exception {
        SystemPolicyResponse response = financeConfigService.putSystemPolicy(
                systemPrincipal(),
                "payroll.tax",
                new PutSystemPolicyRequest(objectMapper.readTree("{\"rate\":0.12}"), "Tax policy")
        );

        assertThat(response.policyKey()).isEqualTo("payroll.tax");
        assertThat(response.policyValue().path("rate").decimalValue()).isEqualByComparingTo("0.12");
        assertThat(financeConfigService.getSystemPolicy(systemPrincipal(), "payroll.tax").description()).isEqualTo("Tax policy");
    }

    @Test
    void shouldFallbackDocumentNumberWhenRuleIsMissing() {
        String number = financeConfigService.nextDocumentNumber("MISSING_TYPE");

        assertThat(number).startsWith("MISSING_TYPE-");
    }

    private FernPrincipal systemPrincipal() {
        return new FernPrincipal(
                1L,
                "finance-config-admin",
                Set.of("admin"),
                Set.of(PermissionCodes.FINANCE_CONFIG_READ, PermissionCodes.FINANCE_CONFIG_WRITE),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "finance-config-jti"
        );
    }
}
