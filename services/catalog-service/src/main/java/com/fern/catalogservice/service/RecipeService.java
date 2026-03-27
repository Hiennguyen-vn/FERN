package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.IngredientEntity;
import com.fern.catalogservice.domain.IngredientStatus;
import com.fern.catalogservice.domain.RecipeEntity;
import com.fern.catalogservice.domain.RecipeVersionEntity;
import com.fern.catalogservice.domain.RecipeVersionIngredientEntity;
import com.fern.catalogservice.domain.RecipeVersionStatus;
import com.fern.catalogservice.dto.RecipeResponse;
import com.fern.catalogservice.dto.RecipeUpsertRequest;
import com.fern.catalogservice.dto.RecipeVersionIngredientRequest;
import com.fern.catalogservice.dto.RecipeVersionIngredientResponse;
import com.fern.catalogservice.dto.RecipeVersionResponse;
import com.fern.catalogservice.dto.RecipeVersionUpsertRequest;
import com.fern.catalogservice.repository.RecipeRepository;
import com.fern.catalogservice.repository.RecipeVersionIngredientRepository;
import com.fern.catalogservice.repository.RecipeVersionRepository;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeService {
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository recipeVersionRepository;
    private final RecipeVersionIngredientRepository recipeVersionIngredientRepository;
    private final ProductService productService;
    private final IngredientService ingredientService;
    private final CatalogReferenceService catalogReferenceService;
    private final CatalogAuditService catalogAuditService;
    private final CatalogOutboxService catalogOutboxService;

    public RecipeService(
            RecipeRepository recipeRepository,
            RecipeVersionRepository recipeVersionRepository,
            RecipeVersionIngredientRepository recipeVersionIngredientRepository,
            ProductService productService,
            IngredientService ingredientService,
            CatalogReferenceService catalogReferenceService,
            CatalogAuditService catalogAuditService,
            CatalogOutboxService catalogOutboxService
    ) {
        this.recipeRepository = recipeRepository;
        this.recipeVersionRepository = recipeVersionRepository;
        this.recipeVersionIngredientRepository = recipeVersionIngredientRepository;
        this.productService = productService;
        this.ingredientService = ingredientService;
        this.catalogReferenceService = catalogReferenceService;
        this.catalogAuditService = catalogAuditService;
        this.catalogOutboxService = catalogOutboxService;
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> listRecipes() {
        return recipeRepository.findAllByOrderByRecipeCodeAsc().stream().map(this::toRecipeResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(Long id) {
        return toRecipeResponse(requireRecipe(id));
    }

    @Transactional
    public RecipeResponse createRecipe(FernPrincipal principal, RecipeUpsertRequest request) {
        if (recipeRepository.findByProduct_Id(request.productId()).isPresent()) {
            throw new ConflictException("Recipe already exists for product");
        }
        if (recipeRepository.existsByRecipeCodeIgnoreCase(request.recipeCode())) {
            throw new ConflictException("Recipe code already exists");
        }
        RecipeEntity entity = new RecipeEntity();
        applyRecipe(entity, principal, request);
        entity = recipeRepository.save(entity);
        RecipeResponse response = toRecipeResponse(entity);
        catalogAuditService.publish("catalog.recipe.changed", principal, null, null, "CREATE_RECIPE", "recipe", String.valueOf(entity.getId()), null, response, Map.of("recipeCode", entity.getRecipeCode()));
        return response;
    }

    @Transactional
    public RecipeResponse updateRecipe(FernPrincipal principal, Long id, RecipeUpsertRequest request) {
        RecipeEntity entity = requireRecipe(id);
        RecipeEntity existingForProduct = recipeRepository.findByProduct_Id(request.productId()).orElse(null);
        if (existingForProduct != null && !existingForProduct.getId().equals(id)) {
            throw new ConflictException("Recipe already exists for product");
        }
        if (recipeRepository.existsByRecipeCodeIgnoreCaseAndIdNot(request.recipeCode(), id)) {
            throw new ConflictException("Recipe code already exists");
        }
        RecipeResponse before = toRecipeResponse(entity);
        applyRecipe(entity, principal, request);
        entity = recipeRepository.save(entity);
        RecipeResponse response = toRecipeResponse(entity);
        catalogAuditService.publish("catalog.recipe.changed", principal, null, null, "UPDATE_RECIPE", "recipe", String.valueOf(entity.getId()), before, response, Map.of("recipeCode", entity.getRecipeCode()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<RecipeVersionResponse> listRecipeVersions(Long recipeId) {
        requireRecipe(recipeId);
        return recipeVersionRepository.findByRecipe_IdOrderByEffectiveFromDesc(recipeId).stream().map(this::toRecipeVersionResponse).toList();
    }

    @Transactional(readOnly = true)
    public RecipeVersionResponse getRecipeVersion(Long id) {
        return toRecipeVersionResponse(requireRecipeVersion(id));
    }

    @Transactional
    public RecipeVersionResponse createRecipeVersion(FernPrincipal principal, RecipeVersionUpsertRequest request) {
        RecipeEntity recipe = requireRecipe(request.recipeId());
        ensureUniqueVersionNo(recipe, request.versionNo(), null);
        List<IngredientEntity> ingredients = validateIngredientLines(request.ingredients());
        validateVersionState(recipe, null, request.status(), request.effectiveFrom(), request.effectiveTo(), ingredients);

        RecipeVersionEntity entity = new RecipeVersionEntity();
        applyVersion(entity, principal, recipe, request);
        entity = recipeVersionRepository.save(entity);
        replaceIngredientLines(entity, request.ingredients(), ingredients);

        RecipeVersionResponse response = toRecipeVersionResponse(entity);
        catalogAuditService.publish("catalog.recipe.version.changed", principal, null, null, "CREATE_RECIPE_VERSION", "recipe_version", String.valueOf(entity.getId()), null, response, Map.of("recipeId", recipe.getId()));
        if (entity.getStatus() == RecipeVersionStatus.ACTIVE) {
            publishVersionActivated(principal, entity, response);
        }
        return response;
    }

    @Transactional
    public RecipeVersionResponse updateRecipeVersion(FernPrincipal principal, Long id, RecipeVersionUpsertRequest request) {
        RecipeVersionEntity entity = requireRecipeVersion(id);
        RecipeEntity recipe = requireRecipe(request.recipeId());
        ensureUniqueVersionNo(recipe, request.versionNo(), id);
        List<IngredientEntity> ingredients = validateIngredientLines(request.ingredients());
        validateVersionState(recipe, id, request.status(), request.effectiveFrom(), request.effectiveTo(), ingredients);

        RecipeVersionResponse before = toRecipeVersionResponse(entity);
        applyVersion(entity, principal, recipe, request);
        entity = recipeVersionRepository.save(entity);
        replaceIngredientLines(entity, request.ingredients(), ingredients);

        RecipeVersionResponse response = toRecipeVersionResponse(entity);
        catalogAuditService.publish("catalog.recipe.version.changed", principal, null, null, "UPDATE_RECIPE_VERSION", "recipe_version", String.valueOf(entity.getId()), before, response, Map.of("recipeId", recipe.getId()));
        if (entity.getStatus() == RecipeVersionStatus.ACTIVE) {
            publishVersionActivated(principal, entity, response);
        }
        return response;
    }

    @Transactional
    public RecipeVersionResponse activateRecipeVersion(FernPrincipal principal, Long id) {
        RecipeVersionEntity entity = requireRecipeVersion(id);
        List<IngredientEntity> ingredients = recipeVersionIngredientRepository.findByRecipeVersion_IdOrderBySortOrderAsc(id).stream()
                .map(RecipeVersionIngredientEntity::getIngredient)
                .toList();
        validateVersionState(entity.getRecipe(), id, RecipeVersionStatus.ACTIVE, entity.getEffectiveFrom(), entity.getEffectiveTo(), ingredients);
        RecipeVersionResponse before = toRecipeVersionResponse(entity);
        entity.setStatus(RecipeVersionStatus.ACTIVE);
        entity = recipeVersionRepository.save(entity);
        RecipeVersionResponse response = toRecipeVersionResponse(entity);
        catalogAuditService.publish("catalog.recipe.version.activated", principal, null, null, "ACTIVATE_RECIPE_VERSION", "recipe_version", String.valueOf(entity.getId()), before, response, Map.of("recipeId", entity.getRecipe().getId()));
        publishVersionActivated(principal, entity, response);
        return response;
    }

    @Transactional
    public RecipeVersionResponse archiveRecipeVersion(FernPrincipal principal, Long id) {
        RecipeVersionEntity entity = requireRecipeVersion(id);
        RecipeVersionResponse before = toRecipeVersionResponse(entity);
        entity.setStatus(RecipeVersionStatus.ARCHIVED);
        entity = recipeVersionRepository.save(entity);
        RecipeVersionResponse response = toRecipeVersionResponse(entity);
        catalogAuditService.publish("catalog.recipe.version.changed", principal, null, null, "ARCHIVE_RECIPE_VERSION", "recipe_version", String.valueOf(entity.getId()), before, response, Map.of("recipeId", entity.getRecipe().getId()));
        return response;
    }

    @Transactional(readOnly = true)
    public RecipeEntity requireRecipe(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
    }

    @Transactional(readOnly = true)
    public RecipeVersionEntity requireRecipeVersion(Long id) {
        return recipeVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe version not found"));
    }

    private void applyRecipe(RecipeEntity entity, FernPrincipal principal, RecipeUpsertRequest request) {
        entity.setProduct(productService.requireProduct(request.productId()));
        entity.setRecipeCode(request.recipeCode());
        entity.setDescription(request.description());
        if (entity.getId() == null) {
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
        }
    }

    private void applyVersion(RecipeVersionEntity entity, FernPrincipal principal, RecipeEntity recipe, RecipeVersionUpsertRequest request) {
        catalogReferenceService.ensureUnitExists(request.yieldUomCode());
        entity.setRecipe(recipe);
        entity.setVersionNo(request.versionNo());
        entity.setYieldQty(request.yieldQty());
        entity.setYieldUomCode(request.yieldUomCode());
        entity.setStatus(request.status());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        if (entity.getId() == null) {
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
        }
    }

    private void ensureUniqueVersionNo(RecipeEntity recipe, String versionNo, Long excludeId) {
        boolean duplicate = recipeVersionRepository.findByRecipe_IdOrderByEffectiveFromDesc(recipe.getId()).stream()
                .anyMatch(existing -> !existing.getId().equals(excludeId) && existing.getVersionNo().equalsIgnoreCase(versionNo));
        if (duplicate) {
            throw new ConflictException("Recipe version already exists");
        }
    }

    private List<IngredientEntity> validateIngredientLines(List<RecipeVersionIngredientRequest> requests) {
        Set<Long> seenIngredientIds = new HashSet<>();
        return requests.stream()
                .sorted(Comparator.comparingInt(RecipeVersionIngredientRequest::sortOrder))
                .map(request -> {
                    if (!seenIngredientIds.add(request.ingredientId())) {
                        throw new BadRequestException("Duplicate ingredient lines are not allowed for the same recipe version");
                    }
                    catalogReferenceService.ensureUnitExists(request.uomCode());
                    return ingredientService.requireIngredient(request.ingredientId());
                })
                .toList();
    }

    private void validateVersionState(
            RecipeEntity recipe,
            Long excludeId,
            RecipeVersionStatus status,
            java.time.LocalDate effectiveFrom,
            java.time.LocalDate effectiveTo,
            List<IngredientEntity> ingredients
    ) {
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new ConflictException("Recipe version effectiveTo must be on or after effectiveFrom");
        }
        if (status != RecipeVersionStatus.ACTIVE) {
            return;
        }
        if (ingredients.isEmpty()) {
            throw new ConflictException("Recipe version must contain at least one ingredient");
        }
        if (ingredients.stream().anyMatch(ingredient -> ingredient.getStatus() == IngredientStatus.DISCONTINUED)) {
            throw new ConflictException("Discontinued ingredient cannot be used for an active recipe version");
        }
        boolean overlaps = recipeVersionRepository.findByRecipe_IdOrderByEffectiveFromDesc(recipe.getId()).stream()
                .filter(existing -> existing.getStatus() == RecipeVersionStatus.ACTIVE)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .anyMatch(existing -> EffectiveDateSupport.overlaps(existing.getEffectiveFrom(), existing.getEffectiveTo(), effectiveFrom, effectiveTo));
        if (overlaps) {
            throw new ConflictException("Another active recipe version overlaps the requested effective period");
        }
    }

    private void replaceIngredientLines(
            RecipeVersionEntity version,
            List<RecipeVersionIngredientRequest> requests,
            List<IngredientEntity> ingredients
    ) {
        recipeVersionIngredientRepository.deleteByRecipeVersion_Id(version.getId());
        Map<Long, IngredientEntity> ingredientById = ingredients.stream().collect(Collectors.toMap(IngredientEntity::getId, Function.identity()));
        for (RecipeVersionIngredientRequest request : requests) {
            RecipeVersionIngredientEntity line = new RecipeVersionIngredientEntity();
            line.setRecipeVersion(version);
            line.setIngredient(ingredientById.get(request.ingredientId()));
            line.setUomCode(request.uomCode());
            line.setQty(request.qty());
            line.setSortOrder(request.sortOrder());
            recipeVersionIngredientRepository.save(line);
        }
    }

    private void publishVersionActivated(FernPrincipal principal, RecipeVersionEntity entity, RecipeVersionResponse response) {
        catalogOutboxService.enqueue("recipe_version", String.valueOf(entity.getId()), "catalog.recipe.version.activated", String.valueOf(entity.getRecipe().getProduct().getId()), response);
    }

    private RecipeResponse toRecipeResponse(RecipeEntity entity) {
        return new RecipeResponse(entity.getId(), entity.getProduct().getId(), entity.getRecipeCode(), entity.getDescription());
    }

    private RecipeVersionResponse toRecipeVersionResponse(RecipeVersionEntity entity) {
        List<RecipeVersionIngredientResponse> ingredients = recipeVersionIngredientRepository.findByRecipeVersion_IdOrderBySortOrderAsc(entity.getId()).stream()
                .map(line -> new RecipeVersionIngredientResponse(
                        line.getIngredient().getId(),
                        line.getIngredient().getCode(),
                        line.getIngredient().getName(),
                        line.getUomCode(),
                        line.getQty(),
                        line.getSortOrder()
                ))
                .toList();
        return new RecipeVersionResponse(
                entity.getId(),
                entity.getRecipe().getId(),
                entity.getVersionNo(),
                entity.getYieldQty(),
                entity.getYieldUomCode(),
                entity.getStatus(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                ingredients
        );
    }
}
