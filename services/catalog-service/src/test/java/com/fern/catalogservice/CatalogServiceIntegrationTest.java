package com.fern.catalogservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fern.platform.audit.AuditEventPublisher;
import com.fern.platform.common.FernPrincipalType;
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
class CatalogServiceIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    private String userToken;
    private String serviceToken;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> FernIntegrationContainers.masterJdbcUrl("catalog"));
        registry.add("spring.datasource.username", FernIntegrationContainers::jdbcUsername);
        registry.add("spring.datasource.password", FernIntegrationContainers::jdbcPassword);
        registry.add("spring.data.redis.host", FernIntegrationContainers::redisHost);
        registry.add("spring.data.redis.port", FernIntegrationContainers::redisPort);
        registry.add("fern.outbox.enabled", () -> false);
    }

    @BeforeEach
    void setUp() {
        doNothing().when(auditEventPublisher).publishAuditEvent(org.mockito.ArgumentMatchers.any());
        doNothing().when(auditEventPublisher).publishSecurityEvent(org.mockito.ArgumentMatchers.any());
        doNothing().when(auditEventPublisher).publishRequestTrace(org.mockito.ArgumentMatchers.any());

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
        redisTemplate.delete("fern:versions:policy");
        redisTemplate.delete("fern:versions:scope");

        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        userToken = jwtService.encode(new FernJwtClaims(
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
                new ScopeRoots(true, List.of(1L), List.of()),
                1L,
                1L,
                "catalog-test-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());
        serviceToken = jwtService.encode(new FernJwtClaims(
                null,
                "pos-service",
                Set.of(),
                Set.of("catalog.internal.resolve"),
                new ScopeRoots(true, List.of(), List.of()),
                0L,
                0L,
                "catalog-service-test-jti",
                Instant.now(),
                Instant.now().plusSeconds(300),
                FernPrincipalType.SERVICE
        ), jwtService.serviceTokenTtl());
    }

    @Test
    void shouldManageCatalogAndResolveInternalReads() throws Exception {
        seedReferenceData();

        Long ingredientId = createIngredient("ING-COFFEE", "Coffee Beans", "ING", "GRAM", "ACTIVE");
        Long productId = createProduct("PROD-LATTE", "Latte", "BEV", "ACTIVE");
        Long recipeId = createRecipe(productId, "RCP-LATTE");
        Long versionId = createRecipeVersion(recipeId, "v1", LocalDate.of(2026, 3, 1), null, ingredientId, "GRAM", "10.0000", "ACTIVE");

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId));

        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "GLOBAL",
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 55000.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId));

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outletId").value(101));

        mockMvc.perform(get("/internal/catalog/menu")
                        .header("Authorization", serviceBearer())
                        .param("outletId", "101")
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].priceValue").value(55000.00))
                .andExpect(jsonPath("$.items[0].taxPercent").value(10.00));

        mockMvc.perform(get("/internal/catalog/price-resolution")
                        .header("Authorization", serviceBearer())
                        .param("productId", String.valueOf(productId))
                        .param("outletId", "101")
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("GLOBAL"))
                .andExpect(jsonPath("$.priceValue").value(55000.00))
                .andExpect(jsonPath("$.taxPercent").value(10.00));

        mockMvc.perform(get("/internal/catalog/recipe-resolution")
                        .header("Authorization", serviceBearer())
                        .param("productId", String.valueOf(productId))
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeVersionId").value(versionId))
                .andExpect(jsonPath("$.ingredients[0].ingredientId").value(ingredientId));

        mockMvc.perform(get("/internal/catalog/recipe-resolutions")
                        .header("Authorization", serviceBearer())
                        .param("productIds", String.valueOf(productId))
                        .param("productIds", String.valueOf(productId))
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipeVersionId").value(versionId))
                .andExpect(jsonPath("$[0].ingredients[0].ingredientId").value(ingredientId))
                .andExpect(jsonPath("$.length()").value(1));

        List<String> eventTypes = jdbcTemplate.queryForList("SELECT event_type FROM catalog.outbox_event ORDER BY created_at", String.class);
        assertThat(eventTypes).contains("catalog.product.changed", "catalog.recipe.version.activated", "catalog.price.published", "catalog.availability.changed");
    }

    @Test
    void shouldRejectOverlappingTaxAndPriceWindows() throws Exception {
        seedReferenceData();
        Long productId = createProduct("PROD-TEA", "Tea", "BEV", "ACTIVE");

        mockMvc.perform(post("/tax-rates")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "taxPercent": 8.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tax-rates")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "taxPercent": 10.00,
                                  "effectiveFrom": "2026-03-15"
                                }
                                """.formatted(productId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "GLOBAL",
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 25000.00,
                                  "effectiveFrom": "2026-03-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "GLOBAL",
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 26000.00,
                                  "effectiveFrom": "2026-03-10"
                                }
                                """.formatted(productId)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldManagePromotionsViaCrudEndpoints() throws Exception {
        Long promotionId = createPromotion(
                "PROMO-CRUD",
                "Launch Promo",
                "GLOBAL",
                null,
                "10.00",
                null,
                "100000.00",
                500,
                LocalDate.of(2026, 3, 1),
                null
        );

        mockMvc.perform(get("/catalog/promotions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(promotionId))
                .andExpect(jsonPath("$[0].code").value("PROMO-CRUD"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(put("/catalog/promotions/{id}", promotionId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "PROMO-CRUD",
                                  "name": "Launch Promo Updated",
                                  "description": "Updated launch campaign",
                                  "promotionType": "ORDER",
                                  "discountPercent": null,
                                  "discountAmount": 15000.00,
                                  "scopeType": "GLOBAL",
                                  "scopeId": null,
                                  "minOrderAmount": 120000.00,
                                  "maxUsageTotal": 600,
                                  "effectiveFrom": "2026-03-01",
                                  "effectiveTo": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Launch Promo Updated"))
                .andExpect(jsonPath("$.discountAmount").value(15000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/catalog/promotions/{id}/deactivate", promotionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/catalog/promotions")
                        .header("Authorization", bearer())
                        .param("scopeType", "GLOBAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));
    }

    @Test
    void shouldListPromotionsByScopeAndResolveMostSpecificMatch() throws Exception {
        createPromotion(
                "SAVE10",
                "Global Save 10",
                "GLOBAL",
                null,
                "10.00",
                null,
                "50000.00",
                null,
                LocalDate.of(2026, 3, 1),
                null
        );
        createPromotion(
                "SAVE10",
                "Region Save 10",
                "REGION",
                202L,
                null,
                "12000.00",
                "50000.00",
                null,
                LocalDate.of(2026, 3, 2),
                null
        );
        createPromotion(
                "SAVE10",
                "Outlet Save 10",
                "OUTLET",
                101L,
                null,
                "15000.00",
                "50000.00",
                null,
                LocalDate.of(2026, 3, 3),
                null
        );

        mockMvc.perform(get("/catalog/promotions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].scopeType").value("OUTLET"))
                .andExpect(jsonPath("$[1].scopeType").value("REGION"))
                .andExpect(jsonPath("$[2].scopeType").value("GLOBAL"));

        mockMvc.perform(get("/catalog/promotions")
                        .header("Authorization", bearer())
                        .param("scopeType", "REGION")
                        .param("scopeId", "202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("SAVE10"))
                .andExpect(jsonPath("$[0].scopeType").value("REGION"));

        mockMvc.perform(get("/internal/catalog/promotion-resolution")
                        .header("Authorization", serviceBearer())
                        .param("code", "SAVE10")
                        .param("outletId", "101")
                        .param("regionId", "202")
                        .param("orderTotal", "100000.00")
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("OUTLET"))
                .andExpect(jsonPath("$.scopeId").value(101));

        mockMvc.perform(get("/internal/catalog/promotion-resolution")
                        .header("Authorization", serviceBearer())
                        .param("code", "SAVE10")
                        .param("outletId", "999")
                        .param("regionId", "202")
                        .param("orderTotal", "100000.00")
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("REGION"))
                .andExpect(jsonPath("$.scopeId").value(202));

        mockMvc.perform(get("/internal/catalog/promotion-resolution")
                        .header("Authorization", serviceBearer())
                        .param("code", "SAVE10")
                        .param("outletId", "999")
                        .param("regionId", "999")
                        .param("orderTotal", "100000.00")
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeType").value("GLOBAL"));
    }

    @Test
    void shouldRejectInvalidOrConflictingPromotionRequests() throws Exception {
        createPromotion(
                "PROMO-CONFLICT",
                "Original Promotion",
                "GLOBAL",
                null,
                "10.00",
                null,
                null,
                null,
                LocalDate.of(2026, 3, 1),
                null
        );

        mockMvc.perform(post("/catalog/promotions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "PROMO-CONFLICT",
                                  "name": "Overlapping Promotion",
                                  "description": "Should conflict",
                                  "promotionType": "ORDER",
                                  "discountPercent": 15.00,
                                  "discountAmount": null,
                                  "scopeType": "GLOBAL",
                                  "scopeId": null,
                                  "minOrderAmount": null,
                                  "maxUsageTotal": null,
                                  "effectiveFrom": "2026-03-10",
                                  "effectiveTo": null
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/catalog/promotions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "PROMO-DATE",
                                  "name": "Invalid Date Promotion",
                                  "description": "Should fail date validation",
                                  "promotionType": "ORDER",
                                  "discountPercent": 10.00,
                                  "discountAmount": null,
                                  "scopeType": "GLOBAL",
                                  "scopeId": null,
                                  "minOrderAmount": null,
                                  "maxUsageTotal": null,
                                  "effectiveFrom": "2026-03-20",
                                  "effectiveTo": "2026-03-10"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/catalog/promotions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "PROMO-DISCOUNT",
                                  "name": "Invalid Discount Promotion",
                                  "description": "Should fail discount validation",
                                  "promotionType": "ORDER",
                                  "discountPercent": 10.00,
                                  "discountAmount": 5000.00,
                                  "scopeType": "GLOBAL",
                                  "scopeId": null,
                                  "minOrderAmount": null,
                                  "maxUsageTotal": null,
                                  "effectiveFrom": "2026-03-01",
                                  "effectiveTo": null
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenPromotionResolutionHasNoApplicableMatch() throws Exception {
        createPromotion(
                "PROMO-NOPE",
                "Below Minimum",
                "GLOBAL",
                null,
                "10.00",
                null,
                "500000.00",
                null,
                LocalDate.of(2026, 3, 1),
                null
        );
        createPromotion(
                "PROMO-NOPE",
                "Future Region Promotion",
                "REGION",
                202L,
                null,
                "12000.00",
                null,
                null,
                LocalDate.of(2026, 4, 1),
                null
        );
        createPromotion(
                "PROMO-NOPE",
                "Wrong Outlet Promotion",
                "OUTLET",
                303L,
                null,
                "15000.00",
                null,
                null,
                LocalDate.of(2026, 3, 1),
                null
        );
        Long inactivePromotionId = createPromotion(
                "PROMO-NOPE",
                "Inactive Outlet Promotion",
                "OUTLET",
                101L,
                null,
                "18000.00",
                null,
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );
        mockMvc.perform(post("/catalog/promotions/{id}/deactivate", inactivePromotionId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/internal/catalog/promotion-resolution")
                        .header("Authorization", serviceBearer())
                        .param("code", "PROMO-NOPE")
                        .param("outletId", "101")
                        .param("regionId", "202")
                        .param("orderTotal", "100000.00")
                        .param("at", "2026-03-15"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectStaleTokenAtServiceBoundary() throws Exception {
        redisTemplate.opsForValue().set("fern:versions:policy", "2");

        mockMvc.perform(get("/products")
                        .header("Authorization", bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUserPrincipalOnInternalCatalogEndpoints() throws Exception {
        FernJwtProperties properties = new FernJwtProperties();
        properties.setSecret("XV4T89da-00NoHY48hZTYhGdaCNpqooKVy4MDKTRO5v4Im6TwlAITKb6_O4K--Iv");
        FernJwtService jwtService = new FernJwtService(properties, Clock.systemUTC());
        String userInternalToken = jwtService.encode(new FernJwtClaims(
                1L,
                "bootstrap-admin",
                Set.of("bootstrap_admin"),
                Set.of("catalog.internal.resolve"),
                new ScopeRoots(true, List.of(), List.of()),
                1L,
                1L,
                "catalog-user-internal-jti",
                Instant.now(),
                Instant.now().plusSeconds(900)
        ), jwtService.accessTokenTtl());

        mockMvc.perform(get("/internal/catalog/menu")
                        .header("Authorization", "Bearer " + userInternalToken)
                        .param("outletId", "101")
                        .param("at", "2026-03-15"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectActivatingRecipeVersionWithDiscontinuedIngredient() throws Exception {
        seedReferenceData();
        Long ingredientId = createIngredient("ING-OLD", "Old Ingredient", "ING", "GRAM", "DISCONTINUED");
        Long productId = createProduct("PROD-MIX", "Mix", "BEV", "ACTIVE");
        Long recipeId = createRecipe(productId, "RCP-MIX");
        Long versionId = createRecipeVersion(recipeId, "draft-1", LocalDate.of(2026, 3, 1), null, ingredientId, "GRAM", "5.0000", "DRAFT");

        mockMvc.perform(post("/recipe-versions/%d/activate".formatted(versionId))
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldResolvePriceAndRecipeByTimestamp() throws Exception {
        seedReferenceData();
        Long ingredientId = createIngredient("ING-MILK", "Milk", "ING", "GRAM", "ACTIVE");
        Long productId = createProduct("PROD-CAP", "Cappuccino", "BEV", "ACTIVE");
        Long recipeId = createRecipe(productId, "RCP-CAP");

        createRecipeVersion(recipeId, "v1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), ingredientId, "GRAM", "8.0000", "ACTIVE");
        createRecipeVersion(recipeId, "v2", LocalDate.of(2026, 4, 1), null, ingredientId, "GRAM", "12.0000", "ACTIVE");

        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "GLOBAL",
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 42000.00,
                                  "effectiveFrom": "2026-01-01",
                                  "effectiveTo": "2026-03-31"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/product-prices")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "scopeType": "GLOBAL",
                                  "priceType": "RETAIL",
                                  "currencyCode": "VND",
                                  "priceValue": 45000.00,
                                  "effectiveFrom": "2026-04-01"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/product-availability")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "outletId": 202,
                                  "available": true
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/internal/catalog/price-resolution")
                        .header("Authorization", serviceBearer())
                        .param("productId", String.valueOf(productId))
                        .param("outletId", "202")
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceValue").value(42000.00));

        mockMvc.perform(get("/internal/catalog/price-resolution")
                        .header("Authorization", serviceBearer())
                        .param("productId", String.valueOf(productId))
                        .param("outletId", "202")
                        .param("at", "2026-04-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceValue").value(45000.00));

        mockMvc.perform(get("/internal/catalog/recipe-resolution")
                        .header("Authorization", serviceBearer())
                        .param("productId", String.valueOf(productId))
                        .param("at", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("v1"))
                .andExpect(jsonPath("$.ingredients[0].qty").value(8.0000));

        mockMvc.perform(get("/internal/catalog/recipe-resolution")
                        .header("Authorization", serviceBearer())
                        .param("productId", String.valueOf(productId))
                        .param("at", "2026-04-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value("v2"))
                .andExpect(jsonPath("$.ingredients[0].qty").value(12.0000));

        mockMvc.perform(get("/internal/catalog/recipe-resolutions")
                        .header("Authorization", serviceBearer())
                        .param("productIds", String.valueOf(productId))
                        .param("productIds", String.valueOf(productId))
                        .param("at", "2026-04-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].versionNo").value("v2"))
                .andExpect(jsonPath("$[0].ingredients[0].qty").value(12.0000))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void shouldRejectDuplicateIngredientLinesWithBadRequest() throws Exception {
        seedReferenceData();
        Long ingredientId = createIngredient("ING-DUP", "Duplicate Ingredient", "ING", "GRAM", "ACTIVE");
        Long productId = createProduct("PROD-DUP", "Duplicate Drink", "BEV", "ACTIVE");
        Long recipeId = createRecipe(productId, "RCP-DUP");

        mockMvc.perform(post("/recipe-versions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": %d,
                                  "versionNo": "v1",
                                  "yieldQty": 1.0000,
                                  "yieldUomCode": "CUP",
                                  "status": "DRAFT",
                                  "effectiveFrom": "2026-03-01",
                                  "ingredients": [
                                    {
                                      "ingredientId": %d,
                                      "uomCode": "GRAM",
                                      "qty": 5.0000,
                                      "sortOrder": 1
                                    },
                                    {
                                      "ingredientId": %d,
                                      "uomCode": "GRAM",
                                      "qty": 6.0000,
                                      "sortOrder": 2
                                    }
                                  ]
                                }
                                """.formatted(recipeId, ingredientId, ingredientId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate ingredient lines are not allowed for the same recipe version"));

        Long versionId = createRecipeVersion(recipeId, "v2", LocalDate.of(2026, 3, 1), null, ingredientId, "GRAM", "5.0000", "DRAFT");

        mockMvc.perform(put("/recipe-versions/%d".formatted(versionId))
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": %d,
                                  "versionNo": "v2",
                                  "yieldQty": 1.0000,
                                  "yieldUomCode": "CUP",
                                  "status": "DRAFT",
                                  "effectiveFrom": "2026-03-01",
                                  "ingredients": [
                                    {
                                      "ingredientId": %d,
                                      "uomCode": "GRAM",
                                      "qty": 5.0000,
                                      "sortOrder": 1
                                    },
                                    {
                                      "ingredientId": %d,
                                      "uomCode": "GRAM",
                                      "qty": 6.0000,
                                      "sortOrder": 2
                                    }
                                  ]
                                }
                                """.formatted(recipeId, ingredientId, ingredientId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate ingredient lines are not allowed for the same recipe version"));
    }

    @Test
    void shouldCreateReferenceDataViaPost() throws Exception {
        mockMvc.perform(post("/product-categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"HOT","name":"Hot Drinks","description":"Hot beverage menu","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("HOT"))
                .andExpect(jsonPath("$.name").value("Hot Drinks"))
                .andExpect(jsonPath("$.description").value("Hot beverage menu"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/ingredient-categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DRY","name":"Dry Goods","description":"Shelf stable ingredients","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DRY"))
                .andExpect(jsonPath("$.name").value("Dry Goods"))
                .andExpect(jsonPath("$.description").value("Shelf stable ingredients"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/units-of-measure")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"LITER","name":"Liter","symbol":"L"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LITER"))
                .andExpect(jsonPath("$.name").value("Liter"))
                .andExpect(jsonPath("$.symbol").value("L"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM catalog.product_category WHERE code = 'HOT'", String.class))
                .isEqualTo("Hot Drinks");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM catalog.ingredient_category WHERE code = 'DRY'", String.class))
                .isEqualTo("Dry Goods");
        assertThat(jdbcTemplate.queryForObject("SELECT symbol FROM catalog.unit_of_measure WHERE code = 'LITER'", String.class))
                .isEqualTo("L");
    }

    @Test
    void shouldRejectDuplicateReferenceCreatesWithoutMutatingExistingData() throws Exception {
        seedReferenceData();

        mockMvc.perform(post("/product-categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BEV","name":"Changed Beverages","description":"Mutated","active":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Product category already exists"));

        mockMvc.perform(post("/ingredient-categories")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ING","name":"Changed Ingredients","description":"Mutated","active":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ingredient category already exists"));

        mockMvc.perform(post("/units-of-measure")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GRAM","name":"Changed Gram","symbol":"kg"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Unit of measure already exists"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM catalog.product_category WHERE code = 'BEV'", String.class))
                .isEqualTo("Beverages");
        assertThat(jdbcTemplate.queryForObject("SELECT is_active FROM catalog.product_category WHERE code = 'BEV'", Boolean.class))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM catalog.ingredient_category WHERE code = 'ING'", String.class))
                .isEqualTo("Ingredients");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM catalog.unit_of_measure WHERE code = 'GRAM'", String.class))
                .isEqualTo("Gram");
    }

    @Test
    void shouldRejectInvalidCatalogInputWithBadRequest() throws Exception {
        seedReferenceData();

        mockMvc.perform(post("/uom-conversions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromUomCode":"GRAM","toUomCode":"GRAM","conversionFactor":1.00000000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("UOM conversion fromUomCode and toUomCode must be different"));

        mockMvc.perform(post("/ingredients")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"ING-NEG",
                                  "name":"Negative Stock",
                                  "categoryCode":"ING",
                                  "baseUomCode":"GRAM",
                                  "minStockLevel": -1.0000,
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingredient minStockLevel must be greater than or equal to 0"));

        mockMvc.perform(post("/ingredients")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"ING-RANGE",
                                  "name":"Invalid Range",
                                  "categoryCode":"ING",
                                  "baseUomCode":"GRAM",
                                  "minStockLevel": 10.0000,
                                  "maxStockLevel": 5.0000,
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingredient maxStockLevel must be greater than or equal to minStockLevel"));
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

    private Long createPromotion(
            String code,
            String name,
            String scopeType,
            Long scopeId,
            String discountPercent,
            String discountAmount,
            String minOrderAmount,
            Integer maxUsageTotal,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/catalog/promotions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "description": "%s",
                                  "promotionType": "ORDER",
                                  "discountPercent": %s,
                                  "discountAmount": %s,
                                  "scopeType": "%s",
                                  "scopeId": %s,
                                  "minOrderAmount": %s,
                                  "maxUsageTotal": %s,
                                  "effectiveFrom": %s,
                                  "effectiveTo": %s
                                }
                                """.formatted(
                                code,
                                name,
                                name,
                                jsonNumber(discountPercent),
                                jsonNumber(discountAmount),
                                scopeType,
                                jsonLong(scopeId),
                                jsonNumber(minOrderAmount),
                                jsonInteger(maxUsageTotal),
                                jsonDate(effectiveFrom),
                                jsonDate(effectiveTo)
                        )))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private Long createRecipeVersion(
            Long recipeId,
            String versionNo,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Long ingredientId,
            String uomCode,
            String qty,
            String status
    ) throws Exception {
        String effectiveToField = effectiveTo == null ? "" : """
                                  "effectiveTo": "%s",
                """.formatted(effectiveTo);

        MvcResult result = mockMvc.perform(post("/recipe-versions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": %d,
                                  "versionNo": "%s",
                                  "yieldQty": 1.0000,
                                  "yieldUomCode": "CUP",
                                  "status": "%s",
                                  "effectiveFrom": "%s",
                %s
                                  "ingredients": [
                                    {
                                      "ingredientId": %d,
                                      "uomCode": "%s",
                                      "qty": %s,
                                      "sortOrder": 1
                                    }
                                  ]
                                }
                                """.formatted(recipeId, versionNo, status, effectiveFrom, effectiveToField, ingredientId, uomCode, qty)))
                .andExpect(status().isOk())
                .andReturn();
        return readId(result);
    }

    private Long readId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.get("id").asLong();
    }

    private String jsonNumber(String value) {
        return value == null ? "null" : value;
    }

    private String jsonLong(Long value) {
        return value == null ? "null" : value.toString();
    }

    private String jsonInteger(Integer value) {
        return value == null ? "null" : value.toString();
    }

    private String jsonDate(LocalDate value) {
        return value == null ? "null" : "\"%s\"".formatted(value);
    }

    private String bearer() {
        return "Bearer " + userToken;
    }

    private String serviceBearer() {
        return "Bearer " + serviceToken;
    }
}
