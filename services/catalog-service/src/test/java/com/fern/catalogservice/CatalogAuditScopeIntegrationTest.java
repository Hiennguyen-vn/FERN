package com.fern.catalogservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEvent;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.ScopeRoots;
import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtProperties;
import com.fern.platform.security.FernJwtService;
import com.fern.platform.testsupport.FernIntegrationContainers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogAuditScopeIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    private String token;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("catalog"));
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
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    catalog.recipe_version_ingredient,
                    catalog.recipe_version,
                    catalog.recipe,
                    catalog.promotion,
                    catalog.product_outlet_availability,
                    catalog.product_price,
                    catalog.tax_rate,
                    catalog.product,
                    catalog.ingredient,
                    catalog.uom_conversion,
                    catalog.unit_of_measure,
                    catalog.product_category,
                    catalog.ingredient_category,
                    catalog.outbox_event
                RESTART IDENTITY CASCADE
                """);

        resetAuditPublisher();

        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        properties.setAllowInsecureDefaultSecret(true);
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        token = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of(
                        "catalog.ingredient.read",
                        "catalog.ingredient.write",
                        "catalog.product.read",
                        "catalog.product.write",
                        "catalog.recipe.read",
                        "catalog.recipe.write",
                        "catalog.price.read",
                        "catalog.price.write",
                        "catalog.promotion.read",
                        "catalog.promotion.write"
                ),
                new ScopeRoots(true, List.of(9L, 10L), List.of(44L, 45L)),
                1L,
                1L,
                "catalog-audit-scope-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
    }

    @Test
    void shouldLeaveUnscopedCatalogMutationsWithoutActorScope() throws Exception {
        seedReferenceData();
        resetAuditPublisher();

        createIngredient("ING-AUDIT", "Audit Ingredient", "ING", "GRAM", "ACTIVE");
        AuditEvent ingredientEvent = captureAuditEvent();
        assertThat(ingredientEvent.eventType()).isEqualTo("catalog.ingredient.changed");
        assertThat(ingredientEvent.regionId()).isNull();
        assertThat(ingredientEvent.outletId()).isNull();

        resetAuditPublisher();
        Long productId = createProduct("PROD-AUDIT", "Audit Drink", "BEV", "ACTIVE");
        AuditEvent productEvent = captureAuditEvent();
        assertThat(productEvent.eventType()).isEqualTo("catalog.product.changed");
        assertThat(productEvent.regionId()).isNull();
        assertThat(productEvent.outletId()).isNull();

        resetAuditPublisher();
        createRecipe(productId, "RCP-AUDIT");
        AuditEvent recipeEvent = captureAuditEvent();
        assertThat(recipeEvent.eventType()).isEqualTo("catalog.recipe.changed");
        assertThat(recipeEvent.regionId()).isNull();
        assertThat(recipeEvent.outletId()).isNull();

        resetAuditPublisher();
        mockMvc.perform(post("/tax-rates")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "taxPercent": 10.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());
        AuditEvent taxEvent = captureAuditEvent();
        assertThat(taxEvent.eventType()).isEqualTo("catalog.tax.changed");
        assertThat(taxEvent.regionId()).isNull();
        assertThat(taxEvent.outletId()).isNull();
    }

    @Test
    void shouldPublishCatalogScopeFromAffectedResource() throws Exception {
        seedReferenceData();
        Long productId = createProduct("PROD-SCOPE", "Scoped Drink", "BEV", "ACTIVE");
        resetAuditPublisher();

        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "GLOBAL",
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 50000.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());
        AuditEvent globalPriceEvent = captureAuditEvent();
        assertThat(globalPriceEvent.eventType()).isEqualTo("catalog.price.published");
        assertThat(globalPriceEvent.regionId()).isNull();
        assertThat(globalPriceEvent.outletId()).isNull();

        resetAuditPublisher();
        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "REGION",
                                  "scopeId": 202,
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 51000.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());
        AuditEvent regionPriceEvent = captureAuditEvent();
        assertThat(regionPriceEvent.eventType()).isEqualTo("catalog.price.published");
        assertThat(regionPriceEvent.regionId()).isEqualTo(202L);
        assertThat(regionPriceEvent.outletId()).isNull();

        resetAuditPublisher();
        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "OUTLET",
                                  "scopeId": 303,
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 52000.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());
        AuditEvent outletPriceEvent = captureAuditEvent();
        assertThat(outletPriceEvent.eventType()).isEqualTo("catalog.price.published");
        assertThat(outletPriceEvent.regionId()).isNull();
        assertThat(outletPriceEvent.outletId()).isEqualTo(303L);

        resetAuditPublisher();
        mockMvc.perform(put("/product-availability")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "outletId": 101,
                                  "available": true
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());
        AuditEvent availabilityEvent = captureAuditEvent();
        assertThat(availabilityEvent.eventType()).isEqualTo("catalog.availability.changed");
        assertThat(availabilityEvent.regionId()).isNull();
        assertThat(availabilityEvent.outletId()).isEqualTo(101L);
    }

    @Test
    void shouldPublishPromotionScopeFromAffectedPromotion() throws Exception {
        resetAuditPublisher();
        createPromotion("PROMO-GLOBAL", "Global Promotion", "GLOBAL", null, LocalDate.of(2026, 3, 1));
        AuditEvent globalPromotionEvent = captureAuditEvent();
        assertThat(globalPromotionEvent.eventType()).isEqualTo("catalog.promotion.changed");
        assertThat(globalPromotionEvent.regionId()).isNull();
        assertThat(globalPromotionEvent.outletId()).isNull();

        resetAuditPublisher();
        createPromotion("PROMO-REGION", "Region Promotion", "REGION", 202L, LocalDate.of(2026, 3, 1));
        AuditEvent regionPromotionEvent = captureAuditEvent();
        assertThat(regionPromotionEvent.eventType()).isEqualTo("catalog.promotion.changed");
        assertThat(regionPromotionEvent.regionId()).isEqualTo(202L);
        assertThat(regionPromotionEvent.outletId()).isNull();

        resetAuditPublisher();
        createPromotion("PROMO-OUTLET", "Outlet Promotion", "OUTLET", 303L, LocalDate.of(2026, 3, 1));
        AuditEvent outletPromotionEvent = captureAuditEvent();
        assertThat(outletPromotionEvent.eventType()).isEqualTo("catalog.promotion.changed");
        assertThat(outletPromotionEvent.regionId()).isNull();
        assertThat(outletPromotionEvent.outletId()).isEqualTo(303L);
    }

    private void seedReferenceData() throws Exception {
        mockMvc.perform(post("/ingredient-categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ING","name":"Ingredients","description":"Ingredient items","active":true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/product-categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BEV","name":"Beverages","description":"Drink menu","active":true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/units-of-measure")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GRAM","name":"Gram","symbol":"g"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/units-of-measure")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CUP","name":"Cup","symbol":"cup"}
                                """))
                .andExpect(status().isOk());
    }

    private Long createIngredient(String code, String name, String categoryCode, String baseUomCode, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/ingredients")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"%s",
                                  "categoryCode":"%s",
                                  "baseUomCode":"%s",
                                  "status":"%s"
                                }
                                """.formatted(code, name, categoryCode, baseUomCode, status)))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private Long createProduct(String code, String name, String categoryCode, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/products")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"%s",
                                  "name":"%s",
                                  "categoryCode":"%s",
                                  "status":"%s"
                                }
                                """.formatted(code, name, categoryCode, status)))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private Long createRecipe(Long productId, String recipeCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/recipes")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "recipeCode": "%s",
                                  "description": "Recipe"
                                }
                                """.formatted(productId, recipeCode)))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private Long createPromotion(String code, String name, String scopeType, Long scopeId, LocalDate effectiveFrom) throws Exception {
        MvcResult result = mockMvc.perform(post("/catalog/promotions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "description": "%s",
                                  "promotionType": "ORDER",
                                  "discountPercent": 10.00,
                                  "discountAmount": null,
                                  "scopeType": "%s",
                                  "scopeId": %s,
                                  "minOrderAmount": null,
                                  "maxUsageTotal": null,
                                  "effectiveFrom": "%s",
                                  "effectiveTo": null
                                }
                                """.formatted(code, name, name, scopeType, scopeId == null ? "null" : scopeId, effectiveFrom)))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private Long readId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("id").asLong();
    }

    private AuditEvent captureAuditEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publishAuditEvent(captor.capture());
        return captor.getValue();
    }

    private void resetAuditPublisher() {
        reset(auditEventPublisher);
        doNothing().when(auditEventPublisher).publishAuditEvent(any());
        doNothing().when(auditEventPublisher).publishSecurityEvent(any());
        doNothing().when(auditEventPublisher).publishRequestTrace(any());
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
