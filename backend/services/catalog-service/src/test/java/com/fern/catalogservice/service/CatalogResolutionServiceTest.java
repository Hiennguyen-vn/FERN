package com.fern.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fern.catalogservice.domain.ProductEntity;
import com.fern.catalogservice.domain.ProductPriceEntity;
import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.PriceType;
import com.fern.catalogservice.domain.TaxRateEntity;
import com.fern.catalogservice.dto.MenuResponse;
import com.fern.catalogservice.repository.ProductOutletAvailabilityRepository;
import com.fern.catalogservice.repository.ProductOutletAvailabilityRepository.AvailableProductSummary;
import com.fern.catalogservice.repository.ProductPriceRepository;
import com.fern.catalogservice.repository.ProductRepository;
import com.fern.catalogservice.repository.RecipeRepository;
import com.fern.catalogservice.repository.RecipeVersionIngredientRepository;
import com.fern.catalogservice.repository.RecipeVersionRepository;
import com.fern.catalogservice.repository.TaxRateRepository;
import com.fern.platform.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogResolutionServiceTest {
    @Mock
    private ProductOutletAvailabilityRepository productOutletAvailabilityRepository;

    @Mock
    private ProductPriceRepository productPriceRepository;

    @Mock
    private TaxRateRepository taxRateRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private RecipeVersionRepository recipeVersionRepository;

    @Mock
    private RecipeVersionIngredientRepository recipeVersionIngredientRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private CatalogResolutionService catalogResolutionService;

    @Test
    void shouldResolveMenuUsingLightweightAvailableProductProjection() {
        AvailableProductSummary summary = new AvailableProductSummary() {
            @Override
            public Long getId() {
                return 10L;
            }

            @Override
            public String getCode() {
                return "BRG-1";
            }

            @Override
            public String getName() {
                return "Burger";
            }

            @Override
            public String getCategoryCode() {
                return "MAIN";
            }
        };
        ProductPriceEntity price = new ProductPriceEntity();
        ProductEntity pricedProduct = new ProductEntity();
        pricedProduct.setId(10L);
        price.setProduct(pricedProduct);
        price.setPriceType(PriceType.RETAIL);
        price.setScopeType(PriceScopeType.OUTLET);
        price.setScopeId(101L);
        price.setCurrencyCode("VND");
        price.setPriceValue(new BigDecimal("55000"));
        TaxRateEntity tax = new TaxRateEntity();
        tax.setProduct(pricedProduct);
        tax.setTaxPercent(new BigDecimal("8"));

        when(productOutletAvailabilityRepository.findAvailableProducts(101L, com.fern.catalogservice.domain.ProductStatus.ACTIVE))
                .thenReturn(List.of(summary));
        when(productPriceRepository.findEffectivePrices(List.of(10L), PriceType.RETAIL, LocalDate.of(2026, 3, 27)))
                .thenReturn(List.of(price));
        when(taxRateRepository.findEffectiveRates(List.of(10L), LocalDate.of(2026, 3, 27)))
                .thenReturn(List.of(tax));

        MenuResponse response = catalogResolutionService.resolveMenu(101L, LocalDate.of(2026, 3, 27), PriceType.RETAIL, 1L, 84L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().productId()).isEqualTo(10L);
        assertThat(response.items().getFirst().productCode()).isEqualTo("BRG-1");
        assertThat(response.items().getFirst().productName()).isEqualTo("Burger");
        assertThat(response.items().getFirst().categoryCode()).isEqualTo("MAIN");
        assertThat(response.items().getFirst().currencyCode()).isEqualTo("VND");
        assertThat(response.items().getFirst().priceValue()).isEqualByComparingTo("55000");
        assertThat(response.items().getFirst().taxPercent()).isEqualByComparingTo("8");
        verifyNoInteractions(productRepository, productService, recipeRepository, recipeVersionRepository, recipeVersionIngredientRepository);
    }

    @Test
    void shouldFailResolveRecipesWhenProductIsMissing() {
        ProductEntity existingProduct = new ProductEntity();
        existingProduct.setId(10L);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(10L, 20L))).thenReturn(List.of(existingProduct));

        assertThatThrownBy(() -> catalogResolutionService.resolveRecipes(List.of(10L, 20L), LocalDate.of(2026, 3, 27)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");

        verifyNoInteractions(recipeRepository, recipeVersionRepository, recipeVersionIngredientRepository, productService);
    }

    @Test
    void shouldFailResolveRecipesWhenRecipeIsMissingForExistingProduct() {
        ProductEntity product = new ProductEntity();
        product.setId(10L);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(10L))).thenReturn(List.of(product));
        when(recipeRepository.findAllByProduct_IdIn(List.of(10L))).thenReturn(List.of());

        assertThatThrownBy(() -> catalogResolutionService.resolveRecipes(List.of(10L), LocalDate.of(2026, 3, 27)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Recipe not found for product");

        verifyNoInteractions(recipeVersionRepository, recipeVersionIngredientRepository, productService);
    }
}
