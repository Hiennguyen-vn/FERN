package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.PriceType;
import com.fern.catalogservice.domain.ProductEntity;
import com.fern.catalogservice.domain.ProductPriceEntity;
import com.fern.catalogservice.domain.ProductStatus;
import com.fern.catalogservice.domain.RecipeEntity;
import com.fern.catalogservice.domain.RecipeVersionEntity;
import com.fern.catalogservice.domain.RecipeVersionIngredientEntity;
import com.fern.catalogservice.domain.RecipeVersionStatus;
import com.fern.catalogservice.domain.TaxRateEntity;
import com.fern.catalogservice.dto.MenuItemResponse;
import com.fern.catalogservice.dto.MenuResponse;
import com.fern.catalogservice.dto.RecipeResolutionResponse;
import com.fern.catalogservice.dto.RecipeVersionIngredientResponse;
import com.fern.catalogservice.dto.ResolvedPriceResponse;
import com.fern.catalogservice.repository.ProductOutletAvailabilityRepository;
import com.fern.catalogservice.repository.ProductPriceRepository;
import com.fern.catalogservice.repository.ProductRepository;
import com.fern.catalogservice.repository.RecipeRepository;
import com.fern.catalogservice.repository.RecipeVersionIngredientRepository;
import com.fern.catalogservice.repository.RecipeVersionRepository;
import com.fern.catalogservice.repository.TaxRateRepository;
import com.fern.platform.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogResolutionService {
    private final ProductOutletAvailabilityRepository productOutletAvailabilityRepository;
    private final ProductPriceRepository productPriceRepository;
    private final TaxRateRepository taxRateRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final RecipeVersionIngredientRepository recipeVersionIngredientRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public CatalogResolutionService(
            ProductOutletAvailabilityRepository productOutletAvailabilityRepository,
            ProductPriceRepository productPriceRepository,
            TaxRateRepository taxRateRepository,
            RecipeRepository recipeRepository,
            RecipeVersionRepository recipeVersionRepository,
            RecipeVersionIngredientRepository recipeVersionIngredientRepository,
            ProductRepository productRepository,
            ProductService productService
    ) {
        this.productOutletAvailabilityRepository = productOutletAvailabilityRepository;
        this.productPriceRepository = productPriceRepository;
        this.taxRateRepository = taxRateRepository;
        this.recipeRepository = recipeRepository;
        this.recipeVersionRepository = recipeVersionRepository;
        this.recipeVersionIngredientRepository = recipeVersionIngredientRepository;
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public MenuResponse resolveMenu(Long outletId, LocalDate businessDate, PriceType priceType, Long regionId, Long countryId) {
        List<ProductEntity> products = productOutletAvailabilityRepository.findAvailableProducts(outletId, ProductStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(ProductEntity::getCode))
                .toList();
        List<Long> productIds = products.stream().map(ProductEntity::getId).toList();
        Map<Long, ProductPriceEntity> resolvedPrices = resolvePrices(productIds, businessDate, priceType, outletId, regionId, countryId);
        Map<Long, TaxRateEntity> resolvedTaxes = resolveTaxes(productIds, businessDate);

        List<MenuItemResponse> items = new ArrayList<>();
        for (ProductEntity product : products) {
            ProductPriceEntity price = resolvedPrices.get(product.getId());
            if (price == null) {
                continue;
            }
            TaxRateEntity tax = resolvedTaxes.get(product.getId());
            items.add(new MenuItemResponse(
                    product.getId(),
                    product.getCode(),
                    product.getName(),
                    product.getCategoryCode(),
                    price.getCurrencyCode(),
                    price.getPriceValue(),
                    tax == null ? null : tax.getTaxPercent()
            ));
        }
        return new MenuResponse(outletId, businessDate, items);
    }

    @Transactional(readOnly = true)
    public ResolvedPriceResponse resolvePrice(Long productId, Long outletId, LocalDate businessDate, PriceType priceType, Long regionId, Long countryId) {
        productService.requireProduct(productId);
        ProductPriceEntity price = resolvePrices(List.of(productId), businessDate, priceType, outletId, regionId, countryId).get(productId);
        if (price == null) {
            throw new ResourceNotFoundException("Effective price not found");
        }
        TaxRateEntity tax = resolveTaxes(List.of(productId), businessDate).get(productId);
        return new ResolvedPriceResponse(
                productId,
                price.getScopeType(),
                price.getScopeId(),
                price.getPriceType(),
                price.getCurrencyCode(),
                price.getPriceValue(),
                tax == null ? null : tax.getTaxPercent(),
                price.getEffectiveFrom(),
                price.getEffectiveTo()
        );
    }

    @Transactional(readOnly = true)
    public RecipeResolutionResponse resolveRecipe(Long productId, LocalDate businessDate) {
        return resolveRecipes(List.of(productId), businessDate).getFirst();
    }

    @Transactional(readOnly = true)
    public List<RecipeResolutionResponse> resolveRecipes(List<Long> productIds, LocalDate businessDate) {
        List<Long> distinctProductIds = new ArrayList<>(new LinkedHashSet<>(productIds));
        if (distinctProductIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> existingProductIds = productRepository.findByIdInAndDeletedAtIsNull(distinctProductIds).stream()
                .map(ProductEntity::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (Long productId : distinctProductIds) {
            if (!existingProductIds.contains(productId)) {
                throw new ResourceNotFoundException("Product not found");
            }
        }

        Map<Long, RecipeEntity> recipesByProductId = recipeRepository.findAllByProduct_IdIn(distinctProductIds).stream()
                .collect(java.util.stream.Collectors.toMap(recipe -> recipe.getProduct().getId(), recipe -> recipe));
        for (Long productId : distinctProductIds) {
            if (!recipesByProductId.containsKey(productId)) {
                throw new ResourceNotFoundException("Recipe not found for product");
            }
        }

        Map<Long, RecipeVersionEntity> effectiveVersionByProductId = new HashMap<>();
        for (RecipeVersionEntity candidate : recipeVersionRepository.findEffectiveVersions(distinctProductIds, RecipeVersionStatus.ACTIVE, businessDate)) {
            effectiveVersionByProductId.putIfAbsent(candidate.getRecipe().getProduct().getId(), candidate);
        }

        List<Long> versionIds = distinctProductIds.stream()
                .map(productId -> {
                    RecipeVersionEntity version = effectiveVersionByProductId.get(productId);
                    if (version == null) {
                        throw new ResourceNotFoundException("Active recipe version not found");
                    }
                    return version.getId();
                })
                .toList();
        Map<Long, List<RecipeVersionIngredientResponse>> ingredientsByVersionId = new HashMap<>();
        for (RecipeVersionIngredientEntity ingredient : recipeVersionIngredientRepository
                .findByRecipeVersion_IdInOrderByRecipeVersion_IdAscSortOrderAsc(versionIds)) {
            ingredientsByVersionId.computeIfAbsent(ingredient.getRecipeVersion().getId(), ignored -> new ArrayList<>())
                    .add(new RecipeVersionIngredientResponse(
                            ingredient.getIngredient().getId(),
                            ingredient.getIngredient().getCode(),
                            ingredient.getIngredient().getName(),
                            ingredient.getUomCode(),
                            ingredient.getQty(),
                            ingredient.getSortOrder()
                    ));
        }

        List<RecipeResolutionResponse> resolved = new ArrayList<>();
        for (Long productId : distinctProductIds) {
            RecipeEntity recipe = recipesByProductId.get(productId);
            RecipeVersionEntity version = effectiveVersionByProductId.get(productId);
            resolved.add(new RecipeResolutionResponse(
                    productId,
                    recipe.getId(),
                    version.getId(),
                    recipe.getRecipeCode(),
                    version.getVersionNo(),
                    version.getEffectiveFrom(),
                    version.getEffectiveTo(),
                    ingredientsByVersionId.getOrDefault(version.getId(), List.of())
            ));
        }
        return resolved;
    }

    private Map<Long, ProductPriceEntity> resolvePrices(
            List<Long> productIds,
            LocalDate businessDate,
            PriceType priceType,
            Long outletId,
            Long regionId,
            Long countryId
    ) {
        Map<Long, ProductPriceEntity> resolved = new HashMap<>();
        for (ProductPriceEntity candidate : productPriceRepository.findEffectivePrices(productIds, priceType, businessDate)) {
            if (!matchesScope(candidate, outletId, regionId, countryId)) {
                continue;
            }
            ProductPriceEntity current = resolved.get(candidate.getProduct().getId());
            if (current == null || scopeRank(candidate.getScopeType()) < scopeRank(current.getScopeType())) {
                resolved.put(candidate.getProduct().getId(), candidate);
            }
        }
        return resolved;
    }

    private Map<Long, TaxRateEntity> resolveTaxes(List<Long> productIds, LocalDate businessDate) {
        Map<Long, TaxRateEntity> resolved = new HashMap<>();
        for (TaxRateEntity candidate : taxRateRepository.findEffectiveRates(productIds, businessDate)) {
            resolved.putIfAbsent(candidate.getProduct().getId(), candidate);
        }
        return resolved;
    }

    private boolean matchesScope(ProductPriceEntity candidate, Long outletId, Long regionId, Long countryId) {
        return switch (candidate.getScopeType()) {
            case GLOBAL -> true;
            case COUNTRY -> candidate.getScopeId() != null && candidate.getScopeId().equals(countryId);
            case REGION -> candidate.getScopeId() != null && candidate.getScopeId().equals(regionId);
            case OUTLET -> candidate.getScopeId() != null && candidate.getScopeId().equals(outletId);
        };
    }

    private int scopeRank(PriceScopeType scopeType) {
        return switch (scopeType) {
            case OUTLET -> 0;
            case REGION -> 1;
            case COUNTRY -> 2;
            case GLOBAL -> 3;
        };
    }
}
