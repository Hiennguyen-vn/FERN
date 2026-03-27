package com.fern.catalogservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.PriceType;
import com.fern.catalogservice.dto.MenuResponse;
import com.fern.catalogservice.dto.PromotionResponse;
import com.fern.catalogservice.dto.RecipeResolutionResponse;
import com.fern.catalogservice.dto.ResolvedPriceResponse;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.CatalogResolutionService;
import com.fern.catalogservice.service.PromotionService;
import com.fern.platform.common.PermissionCodes;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class InternalCatalogControllerTest {
    @Mock
    private CatalogAuthorizer catalogAuthorizer;

    @Mock
    private CatalogResolutionService catalogResolutionService;

    @Mock
    private PromotionService promotionService;

    private InternalCatalogController internalCatalogController;

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-27T23:30:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        internalCatalogController = new InternalCatalogController(catalogAuthorizer, catalogResolutionService, promotionService, clock);
    }

    @Test
    void shouldUseInjectedClockWhenDateIsMissing() {
        MenuResponse response = new MenuResponse(101L, LocalDate.of(2026, 3, 27), List.of());
        when(catalogResolutionService.resolveMenu(101L, LocalDate.of(2026, 3, 27), PriceType.RETAIL, null, null))
                .thenReturn(response);

        MenuResponse actual = internalCatalogController.resolveMenu(null, 101L, null, null, PriceType.RETAIL, null);

        assertThat(actual).isSameAs(response);
        verify(catalogAuthorizer).requireInternalPermission(null, PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        verify(catalogResolutionService).resolveMenu(101L, LocalDate.of(2026, 3, 27), PriceType.RETAIL, null, null);
    }

    @Test
    void shouldPreferExplicitDateOverInjectedClock() {
        LocalDate explicitDate = LocalDate.of(2026, 4, 15);
        ResolvedPriceResponse response = new ResolvedPriceResponse(
                10L,
                PriceScopeType.GLOBAL,
                null,
                PriceType.RETAIL,
                "VND",
                BigDecimal.TEN,
                BigDecimal.ONE,
                explicitDate,
                null
        );
        when(catalogResolutionService.resolvePrice(10L, 101L, explicitDate, PriceType.RETAIL, 2L, 3L))
                .thenReturn(response);

        ResolvedPriceResponse actual = internalCatalogController.resolvePrice(null, 10L, 101L, 2L, 3L, PriceType.RETAIL, explicitDate);

        assertThat(actual).isSameAs(response);
        verify(catalogResolutionService).resolvePrice(10L, 101L, explicitDate, PriceType.RETAIL, 2L, 3L);
    }

    @Test
    void shouldUseInjectedClockForRecipeResolutionWhenDateIsMissing() {
        RecipeResolutionResponse response = new RecipeResolutionResponse(10L, 11L, 12L, "RCP-1", "v1", LocalDate.of(2026, 3, 27), null, List.of());
        when(catalogResolutionService.resolveRecipe(10L, LocalDate.of(2026, 3, 27))).thenReturn(response);

        RecipeResolutionResponse actual = internalCatalogController.resolveRecipe(null, 10L, null);

        assertThat(actual).isSameAs(response);
        verify(catalogResolutionService).resolveRecipe(10L, LocalDate.of(2026, 3, 27));
    }

    @Test
    void shouldUseInjectedClockForBatchRecipeResolutionWhenDateIsMissing() {
        List<RecipeResolutionResponse> response = List.of(
                new RecipeResolutionResponse(10L, 11L, 12L, "RCP-1", "v1", LocalDate.of(2026, 3, 27), null, List.of()),
                new RecipeResolutionResponse(20L, 21L, 22L, "RCP-2", "v3", LocalDate.of(2026, 3, 27), null, List.of())
        );
        when(catalogResolutionService.resolveRecipes(List.of(10L, 20L), LocalDate.of(2026, 3, 27))).thenReturn(response);

        List<RecipeResolutionResponse> actual = internalCatalogController.resolveRecipes(null, List.of(10L, 20L), null);

        assertThat(actual).isSameAs(response);
        verify(catalogResolutionService).resolveRecipes(List.of(10L, 20L), LocalDate.of(2026, 3, 27));
    }

    @Test
    void shouldUseInjectedClockForPromotionResolutionWhenDateIsMissing() {
        PromotionResponse response = new PromotionResponse(
                99L,
                "SAVE10",
                "Spring Save",
                "Spring campaign",
                "ORDER",
                BigDecimal.TEN,
                null,
                "OUTLET",
                101L,
                BigDecimal.valueOf(100_000),
                500,
                LocalDate.of(2026, 3, 1),
                null,
                "ACTIVE",
                1L,
                1L,
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z")
        );
        when(promotionService.resolveApplicablePromotion("SAVE10", 101L, 202L, BigDecimal.valueOf(150_000), LocalDate.of(2026, 3, 27)))
                .thenReturn(Optional.of(response));

        PromotionResponse actual = internalCatalogController.resolvePromotion(null, "SAVE10", 101L, 202L, BigDecimal.valueOf(150_000), null);

        assertThat(actual).isSameAs(response);
        verify(catalogAuthorizer).requireInternalPermission(null, PermissionCodes.CATALOG_INTERNAL_RESOLVE);
        verify(promotionService).resolveApplicablePromotion("SAVE10", 101L, 202L, BigDecimal.valueOf(150_000), LocalDate.of(2026, 3, 27));
    }

    @Test
    void shouldPreferExplicitDateForPromotionResolution() {
        LocalDate explicitDate = LocalDate.of(2026, 4, 15);
        PromotionResponse response = new PromotionResponse(
                100L,
                "SAVE20",
                "April Save",
                "April campaign",
                "ORDER",
                null,
                BigDecimal.valueOf(20_000),
                "GLOBAL",
                null,
                null,
                null,
                LocalDate.of(2026, 4, 1),
                null,
                "ACTIVE",
                1L,
                1L,
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z")
        );
        when(promotionService.resolveApplicablePromotion("SAVE20", 101L, null, BigDecimal.valueOf(200_000), explicitDate))
                .thenReturn(Optional.of(response));

        PromotionResponse actual = internalCatalogController.resolvePromotion(null, "SAVE20", 101L, null, BigDecimal.valueOf(200_000), explicitDate);

        assertThat(actual).isSameAs(response);
        verify(promotionService).resolveApplicablePromotion("SAVE20", 101L, null, BigDecimal.valueOf(200_000), explicitDate);
    }
}
