package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.IngredientEntity;
import com.fern.catalogservice.dto.IngredientResponse;
import com.fern.catalogservice.dto.IngredientUpsertRequest;
import com.fern.catalogservice.repository.IngredientRepository;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientService {
    private final IngredientRepository ingredientRepository;
    private final CatalogReferenceService catalogReferenceService;
    private final CatalogAuditService catalogAuditService;

    public IngredientService(
            IngredientRepository ingredientRepository,
            CatalogReferenceService catalogReferenceService,
            CatalogAuditService catalogAuditService
    ) {
        this.ingredientRepository = ingredientRepository;
        this.catalogReferenceService = catalogReferenceService;
        this.catalogAuditService = catalogAuditService;
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> list() {
        return ingredientRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IngredientResponse get(Long id) {
        return toResponse(requireIngredient(id));
    }

    @Transactional
    public IngredientResponse create(FernPrincipal principal, IngredientUpsertRequest request) {
        if (ingredientRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(request.code())) {
            throw new ConflictException("Ingredient code already exists");
        }
        IngredientEntity entity = new IngredientEntity();
        apply(entity, principal, request, true);
        entity = ingredientRepository.save(entity);
        IngredientResponse response = toResponse(entity);
        catalogAuditService.publish("catalog.ingredient.changed", principal, null, null, "CREATE_INGREDIENT", "ingredient", String.valueOf(entity.getId()), null, response, Map.of("code", entity.getCode()));
        return response;
    }

    @Transactional
    public IngredientResponse update(FernPrincipal principal, Long id, IngredientUpsertRequest request) {
        IngredientEntity entity = requireIngredient(id);
        if (ingredientRepository.existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(request.code(), id)) {
            throw new ConflictException("Ingredient code already exists");
        }
        IngredientResponse before = toResponse(entity);
        apply(entity, principal, request, false);
        entity = ingredientRepository.save(entity);
        IngredientResponse response = toResponse(entity);
        catalogAuditService.publish("catalog.ingredient.changed", principal, null, null, "UPDATE_INGREDIENT", "ingredient", String.valueOf(entity.getId()), before, response, Map.of("code", entity.getCode()));
        return response;
    }

    @Transactional(readOnly = true)
    public IngredientEntity requireIngredient(Long id) {
        return ingredientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
    }

    private void apply(IngredientEntity entity, FernPrincipal principal, IngredientUpsertRequest request, boolean create) {
        catalogReferenceService.ensureIngredientCategoryExists(request.categoryCode());
        catalogReferenceService.ensureUnitExists(request.baseUomCode());
        validateStockLevels(request.minStockLevel(), request.maxStockLevel());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCategoryCode(request.categoryCode());
        entity.setBaseUomCode(request.baseUomCode());
        entity.setMinStockLevel(request.minStockLevel());
        entity.setMaxStockLevel(request.maxStockLevel());
        entity.setStatus(request.status());
        if (create) {
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
        }
        entity.setUpdatedByUserId(principal == null ? null : principal.userId());
    }

    private void validateStockLevels(BigDecimal minStockLevel, BigDecimal maxStockLevel) {
        if (minStockLevel != null && minStockLevel.signum() < 0) {
            throw new BadRequestException("Ingredient minStockLevel must be greater than or equal to 0");
        }
        if (maxStockLevel != null && maxStockLevel.signum() < 0) {
            throw new BadRequestException("Ingredient maxStockLevel must be greater than or equal to 0");
        }
        if (minStockLevel != null && maxStockLevel != null && maxStockLevel.compareTo(minStockLevel) < 0) {
            throw new BadRequestException("Ingredient maxStockLevel must be greater than or equal to minStockLevel");
        }
    }

    private IngredientResponse toResponse(IngredientEntity entity) {
        return new IngredientResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCategoryCode(),
                entity.getBaseUomCode(),
                entity.getMinStockLevel(),
                entity.getMaxStockLevel(),
                entity.getStatus()
        );
    }
}
