package com.fern.orgservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrgServiceIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String token;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("org"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> false);
    }

    @BeforeEach
    void setUp() {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("org.region.read", "org.region.write", "org.outlet.read", "org.outlet.write", "org.scope.resolve"),
                new com.fern.platform.common.ScopeRoots(List.of(1L), List.of()),
                1L,
                1L,
                "org-test-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
        redisTemplate.delete("fern:versions:scope");
    }

    @Test
    void shouldCreateRegionOutletAndExpandScope() throws Exception {
        mockMvc.perform(post("/regions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "REGION-1",
                                  "parentRegionId": 1,
                                  "currencyCode": "VND",
                                  "name": "Region One",
                                  "timezoneName": "Asia/Ho_Chi_Minh"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGION-1"));

        Long regionId = jdbcTemplate.queryForObject("SELECT id FROM org.region WHERE code = 'REGION-1'", Long.class);

        mockMvc.perform(post("/outlets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "regionId": %d,
                                  "code": "OUTLET-1",
                                  "name": "Outlet One",
                                  "status": "ACTIVE"
                                }
                                """.formatted(regionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OUTLET-1"));

        mockMvc.perform(post("/internal/scopes/expand")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"regionIds":[1],"outletIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionIds[0]").value(1))
                .andExpect(jsonPath("$.outletIds[0]").isNumber());

        String version = redisTemplate.opsForValue().get("fern:versions:scope");
        assertThat(version).isNotBlank();
    }
}
