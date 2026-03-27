package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.IngredientCategoryEntity;
import com.fern.catalogservice.domain.ProductCategoryEntity;
import com.fern.catalogservice.domain.UnitOfMeasureEntity;
import com.fern.catalogservice.domain.UomConversionEntity;
import com.fern.catalogservice.domain.UomConversionId;
import com.fern.catalogservice.dto.CategoryRequest;
import com.fern.catalogservice.dto.CategoryResponse;
import com.fern.catalogservice.dto.UnitOfMeasureRequest;
import com.fern.catalogservice.dto.UnitOfMeasureResponse;
import com.fern.catalogservice.dto.UomConversionRequest;
import com.fern.catalogservice.dto.UomConversionResponse;
import com.fern.catalogservice.repository.IngredientCategoryRepository;
import com.fern.catalogservice.repository.ProductCategoryRepository;
import com.fern.catalogservice.repository.UnitOfMeasureRepository;
import com.fern.catalogservice.repository.UomConversionRepository;
import com.fern.platform.common.BadRequestException;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.ResourceNotFoundException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogReferenceService {
    private final ProductCategoryRepository productCategoryRepository;
    private final IngredientCategoryRepository ingredientCategoryRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final UomConversionRepository uomConversionRepository;

    public CatalogReferenceService(
            ProductCategoryRepository productCategoryRepository,
            IngredientCategoryRepository ingredientCategoryRepository,
            UnitOfMeasureRepository unitOfMeasureRepository,
            UomConversionRepository uomConversionRepository
    ) {
        this.productCategoryRepository = productCategoryRepository;
        this.ingredientCategoryRepository = ingredientCategoryRepository;
        this.unitOfMeasureRepository = unitOfMeasureRepository;
        this.uomConversionRepository = uomConversionRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listProductCategories() {
        return productCategoryRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductCategoryEntity::getCode))
                .map(entity -> new CategoryResponse(entity.getCode(), entity.getName(), entity.getDescription(), entity.isActive()))
                .toList();
    }

    @Transactional
    public CategoryResponse createProductCategory(CategoryRequest request) {
        if (productCategoryRepository.existsById(request.code())) {
            throw new ConflictException("Product category already exists");
        }
        ProductCategoryEntity entity = new ProductCategoryEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        return toResponse(productCategoryRepository.save(entity));
    }

    @Transactional
    public CategoryResponse upsertProductCategory(String code, CategoryRequest request) {
        ProductCategoryEntity entity = productCategoryRepository.findById(code).orElseGet(ProductCategoryEntity::new);
        entity.setCode(code);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        return toResponse(productCategoryRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listIngredientCategories() {
        return ingredientCategoryRepository.findAll().stream()
                .sorted(Comparator.comparing(IngredientCategoryEntity::getCode))
                .map(entity -> new CategoryResponse(entity.getCode(), entity.getName(), entity.getDescription(), entity.isActive()))
                .toList();
    }

    @Transactional
    public CategoryResponse createIngredientCategory(CategoryRequest request) {
        if (ingredientCategoryRepository.existsById(request.code())) {
            throw new ConflictException("Ingredient category already exists");
        }
        IngredientCategoryEntity entity = new IngredientCategoryEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        entity = ingredientCategoryRepository.save(entity);
        return new CategoryResponse(entity.getCode(), entity.getName(), entity.getDescription(), entity.isActive());
    }

    @Transactional
    public CategoryResponse upsertIngredientCategory(String code, CategoryRequest request) {
        IngredientCategoryEntity entity = ingredientCategoryRepository.findById(code).orElseGet(IngredientCategoryEntity::new);
        entity.setCode(code);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(request.active());
        return new CategoryResponse(
                ingredientCategoryRepository.save(entity).getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive()
        );
    }

    @Transactional(readOnly = true)
    public List<UnitOfMeasureResponse> listUnitsOfMeasure() {
        return unitOfMeasureRepository.findAll().stream()
                .sorted(Comparator.comparing(UnitOfMeasureEntity::getCode))
                .map(entity -> new UnitOfMeasureResponse(entity.getCode(), entity.getName(), entity.getSymbol()))
                .toList();
    }

    @Transactional
    public UnitOfMeasureResponse createUnitOfMeasure(UnitOfMeasureRequest request) {
        if (unitOfMeasureRepository.existsById(request.code())) {
            throw new ConflictException("Unit of measure already exists");
        }
        UnitOfMeasureEntity entity = new UnitOfMeasureEntity();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setSymbol(request.symbol());
        entity = unitOfMeasureRepository.save(entity);
        return new UnitOfMeasureResponse(entity.getCode(), entity.getName(), entity.getSymbol());
    }

    @Transactional
    public UnitOfMeasureResponse upsertUnitOfMeasure(String code, UnitOfMeasureRequest request) {
        UnitOfMeasureEntity entity = unitOfMeasureRepository.findById(code).orElseGet(UnitOfMeasureEntity::new);
        entity.setCode(code);
        entity.setName(request.name());
        entity.setSymbol(request.symbol());
        entity = unitOfMeasureRepository.save(entity);
        return new UnitOfMeasureResponse(entity.getCode(), entity.getName(), entity.getSymbol());
    }

    @Transactional(readOnly = true)
    public List<UomConversionResponse> listUomConversions() {
        return uomConversionRepository.findAllByOrderByIdFromUomCodeAscIdToUomCodeAsc().stream()
                .map(entity -> new UomConversionResponse(
                        entity.getId().getFromUomCode(),
                        entity.getId().getToUomCode(),
                        entity.getConversionFactor()
                ))
                .toList();
    }

    @Transactional
    public UomConversionResponse upsertUomConversion(UomConversionRequest request) {
        if (request.fromUomCode().equalsIgnoreCase(request.toUomCode())) {
            throw new BadRequestException("UOM conversion fromUomCode and toUomCode must be different");
        }
        ensureUnitExists(request.fromUomCode());
        ensureUnitExists(request.toUomCode());
        UomConversionId id = new UomConversionId(request.fromUomCode(), request.toUomCode());
        UomConversionEntity entity = uomConversionRepository.findById(id).orElseGet(UomConversionEntity::new);
        entity.setId(id);
        entity.setConversionFactor(request.conversionFactor());
        entity = uomConversionRepository.save(entity);
        return new UomConversionResponse(
                entity.getId().getFromUomCode(),
                entity.getId().getToUomCode(),
                entity.getConversionFactor()
        );
    }

    @Transactional(readOnly = true)
    public void ensureIngredientCategoryExists(String categoryCode) {
        if (categoryCode != null && !categoryCode.isBlank() && !ingredientCategoryRepository.existsById(categoryCode)) {
            throw new ResourceNotFoundException("Ingredient category not found");
        }
    }

    @Transactional(readOnly = true)
    public void ensureProductCategoryExists(String categoryCode) {
        if (categoryCode != null && !categoryCode.isBlank() && !productCategoryRepository.existsById(categoryCode)) {
            throw new ResourceNotFoundException("Product category not found");
        }
    }

    @Transactional(readOnly = true)
    public void ensureUnitExists(String unitCode) {
        if (unitCode == null || unitCode.isBlank() || !unitOfMeasureRepository.existsById(unitCode)) {
            throw new ResourceNotFoundException("Unit of measure not found");
        }
    }

    private CategoryResponse toResponse(ProductCategoryEntity entity) {
        return new CategoryResponse(entity.getCode(), entity.getName(), entity.getDescription(), entity.isActive());
    }
}
