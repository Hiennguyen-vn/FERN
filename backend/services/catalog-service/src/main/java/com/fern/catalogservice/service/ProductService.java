package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.ProductEntity;
import com.fern.catalogservice.dto.ProductResponse;
import com.fern.catalogservice.dto.ProductUpsertRequest;
import com.fern.catalogservice.repository.ProductRepository;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final CatalogReferenceService catalogReferenceService;
    private final CatalogAuditService catalogAuditService;
    private final CatalogOutboxService catalogOutboxService;

    public ProductService(
            ProductRepository productRepository,
            CatalogReferenceService catalogReferenceService,
            CatalogAuditService catalogAuditService,
            CatalogOutboxService catalogOutboxService
    ) {
        this.productRepository = productRepository;
        this.catalogReferenceService = catalogReferenceService;
        this.catalogAuditService = catalogAuditService;
        this.catalogOutboxService = catalogOutboxService;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return productRepository.findAllByDeletedAtIsNullOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return toResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse create(FernPrincipal principal, ProductUpsertRequest request) {
        if (productRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(request.code())) {
            throw new ConflictException("Product code already exists");
        }
        ProductEntity entity = new ProductEntity();
        apply(entity, principal, request, true);
        entity = productRepository.save(entity);
        ProductResponse response = toResponse(entity);
        publishProductChanged(principal, entity, null, response);
        return response;
    }

    @Transactional
    public ProductResponse update(FernPrincipal principal, Long id, ProductUpsertRequest request) {
        ProductEntity entity = requireProduct(id);
        if (productRepository.existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(request.code(), id)) {
            throw new ConflictException("Product code already exists");
        }
        ProductResponse before = toResponse(entity);
        apply(entity, principal, request, false);
        entity = productRepository.save(entity);
        ProductResponse response = toResponse(entity);
        publishProductChanged(principal, entity, before, response);
        return response;
    }

    @Transactional(readOnly = true)
    public ProductEntity requireProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void apply(ProductEntity entity, FernPrincipal principal, ProductUpsertRequest request, boolean create) {
        catalogReferenceService.ensureProductCategoryExists(request.categoryCode());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setCategoryCode(request.categoryCode());
        entity.setStatus(request.status());
        entity.setImageUrl(request.imageUrl());
        entity.setDescription(request.description());
        if (create) {
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
        }
        entity.setUpdatedByUserId(principal == null ? null : principal.userId());
    }

    private void publishProductChanged(FernPrincipal principal, ProductEntity entity, ProductResponse before, ProductResponse after) {
        catalogAuditService.publish("catalog.product.changed", principal, null, null, before == null ? "CREATE_PRODUCT" : "UPDATE_PRODUCT", "product", String.valueOf(entity.getId()), before, after, Map.of("code", entity.getCode()));
        catalogOutboxService.enqueue("product", String.valueOf(entity.getId()), "catalog.product.changed", String.valueOf(entity.getId()), after);
    }

    private ProductResponse toResponse(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCategoryCode(),
                entity.getStatus(),
                entity.getImageUrl(),
                entity.getDescription()
        );
    }
}
