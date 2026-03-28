package com.fern.catalogservice.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fern.catalogservice.domain.ProductEntity;
import com.fern.catalogservice.repository.ProductOutletAvailabilityRepository;
import com.fern.catalogservice.repository.ProductPriceRepository;
import com.fern.catalogservice.repository.ProductRepository;
import com.fern.catalogservice.repository.RecipeRepository;
import com.fern.catalogservice.repository.RecipeVersionIngredientRepository;
import com.fern.catalogservice.repository.RecipeVersionRepository;
import com.fern.catalogservice.repository.TaxRateRepository;
import com.fern.platform.common.ResourceNotFoundException;
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
