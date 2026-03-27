package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.ProductOutletAvailabilityEntity;
import com.fern.catalogservice.domain.ProductOutletAvailabilityId;
import com.fern.catalogservice.domain.ProductPriceEntity;
import com.fern.catalogservice.domain.TaxRateEntity;
import com.fern.catalogservice.dto.ProductAvailabilityResponse;
import com.fern.catalogservice.dto.ProductAvailabilityUpsertRequest;
import com.fern.catalogservice.dto.ProductPriceResponse;
import com.fern.catalogservice.dto.ProductPriceUpsertRequest;
import com.fern.catalogservice.dto.TaxRateResponse;
import com.fern.catalogservice.dto.TaxRateUpsertRequest;
import com.fern.catalogservice.repository.ProductOutletAvailabilityRepository;
import com.fern.catalogservice.repository.ProductPriceRepository;
import com.fern.catalogservice.repository.TaxRateRepository;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogPricingService {
    private final ProductPriceRepository productPriceRepository;
    private final TaxRateRepository taxRateRepository;
    private final ProductOutletAvailabilityRepository productOutletAvailabilityRepository;
    private final ProductService productService;
    private final CatalogAuditService catalogAuditService;
    private final CatalogOutboxService catalogOutboxService;

    public CatalogPricingService(
            ProductPriceRepository productPriceRepository,
            TaxRateRepository taxRateRepository,
            ProductOutletAvailabilityRepository productOutletAvailabilityRepository,
            ProductService productService,
            CatalogAuditService catalogAuditService,
            CatalogOutboxService catalogOutboxService
    ) {
        this.productPriceRepository = productPriceRepository;
        this.taxRateRepository = taxRateRepository;
        this.productOutletAvailabilityRepository = productOutletAvailabilityRepository;
        this.productService = productService;
        this.catalogAuditService = catalogAuditService;
        this.catalogOutboxService = catalogOutboxService;
    }

    @Transactional(readOnly = true)
    public List<TaxRateResponse> listTaxRates() {
        return taxRateRepository.findAllByOrderByEffectiveFromDescIdDesc().stream().map(this::toTaxRateResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaxRateResponse getTaxRate(Long id) {
        return toTaxRateResponse(requireTaxRate(id));
    }

    @Transactional
    public TaxRateResponse createTaxRate(FernPrincipal principal, TaxRateUpsertRequest request) {
        validateTaxOverlap(request, null);
        TaxRateEntity entity = new TaxRateEntity();
        applyTaxRate(entity, request);
        entity = taxRateRepository.save(entity);
        TaxRateResponse response = toTaxRateResponse(entity);
        catalogAuditService.publish("catalog.tax.changed", principal, "PUBLISH_TAX_RATE", "tax_rate", String.valueOf(entity.getId()), null, response, Map.of("productId", request.productId()));
        return response;
    }

    @Transactional
    public TaxRateResponse updateTaxRate(FernPrincipal principal, Long id, TaxRateUpsertRequest request) {
        TaxRateEntity entity = requireTaxRate(id);
        TaxRateResponse before = toTaxRateResponse(entity);
        validateTaxOverlap(request, id);
        applyTaxRate(entity, request);
        entity = taxRateRepository.save(entity);
        TaxRateResponse response = toTaxRateResponse(entity);
        catalogAuditService.publish("catalog.tax.changed", principal, "UPDATE_TAX_RATE", "tax_rate", String.valueOf(entity.getId()), before, response, Map.of("productId", request.productId()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProductPriceResponse> listProductPrices() {
        return productPriceRepository.findAllByOrderByEffectiveFromDescIdDesc().stream().map(this::toProductPriceResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductPriceResponse getProductPrice(Long id) {
        return toProductPriceResponse(requireProductPrice(id));
    }

    @Transactional
    public ProductPriceResponse createProductPrice(FernPrincipal principal, ProductPriceUpsertRequest request) {
        validatePriceOverlap(request, null);
        ProductPriceEntity entity = new ProductPriceEntity();
        applyProductPrice(entity, principal, request, true);
        entity = productPriceRepository.save(entity);
        ProductPriceResponse response = toProductPriceResponse(entity);
        publishPrice(principal, entity, null, response);
        return response;
    }

    @Transactional
    public ProductPriceResponse updateProductPrice(FernPrincipal principal, Long id, ProductPriceUpsertRequest request) {
        ProductPriceEntity entity = requireProductPrice(id);
        ProductPriceResponse before = toProductPriceResponse(entity);
        validatePriceOverlap(request, id);
        applyProductPrice(entity, principal, request, false);
        entity = productPriceRepository.save(entity);
        ProductPriceResponse response = toProductPriceResponse(entity);
        publishPrice(principal, entity, before, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProductAvailabilityResponse> listAvailability(Long productId, Long outletId) {
        return productOutletAvailabilityRepository.findAll().stream()
                .filter(entity -> productId == null || entity.getId().getProductId().equals(productId))
                .filter(entity -> outletId == null || entity.getId().getOutletId().equals(outletId))
                .map(this::toAvailabilityResponse)
                .toList();
    }

    @Transactional
    public ProductAvailabilityResponse upsertAvailability(FernPrincipal principal, ProductAvailabilityUpsertRequest request) {
        ProductOutletAvailabilityId id = new ProductOutletAvailabilityId(request.productId(), request.outletId());
        ProductOutletAvailabilityEntity entity = productOutletAvailabilityRepository.findById(id).orElseGet(ProductOutletAvailabilityEntity::new);
        ProductAvailabilityResponse before = entity.getId() == null ? null : toAvailabilityResponse(entity);
        entity.setId(id);
        entity.setProduct(productService.requireProduct(request.productId()));
        entity.setAvailable(request.available());
        entity = productOutletAvailabilityRepository.save(entity);
        ProductAvailabilityResponse response = toAvailabilityResponse(entity);
        catalogAuditService.publish("catalog.availability.changed", principal, "SET_PRODUCT_AVAILABILITY", "product_outlet_availability", request.productId() + ":" + request.outletId(), before, response, Map.of("outletId", request.outletId()));
        catalogOutboxService.enqueue("product_availability", request.productId() + ":" + request.outletId(), "catalog.availability.changed", String.valueOf(request.productId()), response);
        return response;
    }

    @Transactional(readOnly = true)
    public TaxRateEntity requireTaxRate(Long id) {
        return taxRateRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tax rate not found"));
    }

    @Transactional(readOnly = true)
    public ProductPriceEntity requireProductPrice(Long id) {
        return productPriceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product price not found"));
    }

    private void validateTaxOverlap(TaxRateUpsertRequest request, Long excludeId) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new ConflictException("Tax rate effectiveTo must be on or after effectiveFrom");
        }
        boolean overlap = taxRateRepository.findAllByOrderByEffectiveFromDescIdDesc().stream()
                .filter(existing -> existing.getProduct().getId().equals(request.productId()))
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .anyMatch(existing -> EffectiveDateSupport.overlaps(existing.getEffectiveFrom(), existing.getEffectiveTo(), request.effectiveFrom(), request.effectiveTo()));
        if (overlap) {
            throw new ConflictException("Tax rate overlaps an existing effective period");
        }
    }

    private void validatePriceOverlap(ProductPriceUpsertRequest request, Long excludeId) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new ConflictException("Product price effectiveTo must be on or after effectiveFrom");
        }
        if (request.scopeType() == PriceScopeType.GLOBAL && request.scopeId() != null) {
            throw new ConflictException("GLOBAL price scope must not have a scopeId");
        }
        if (request.scopeType() != PriceScopeType.GLOBAL && request.scopeId() == null) {
            throw new ConflictException("Non-global price scope requires a scopeId");
        }
        boolean overlap = productPriceRepository.findAllByOrderByEffectiveFromDescIdDesc().stream()
                .filter(existing -> existing.getProduct().getId().equals(request.productId()))
                .filter(existing -> existing.getScopeType() == request.scopeType())
                .filter(existing -> (existing.getScopeId() == null && request.scopeId() == null)
                        || (existing.getScopeId() != null && existing.getScopeId().equals(request.scopeId())))
                .filter(existing -> existing.getPriceType() == request.priceType())
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .anyMatch(existing -> EffectiveDateSupport.overlaps(existing.getEffectiveFrom(), existing.getEffectiveTo(), request.effectiveFrom(), request.effectiveTo()));
        if (overlap) {
            throw new ConflictException("Product price overlaps an existing effective period");
        }
    }

    private void applyTaxRate(TaxRateEntity entity, TaxRateUpsertRequest request) {
        entity.setProduct(productService.requireProduct(request.productId()));
        entity.setTaxPercent(request.taxPercent());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
    }

    private void applyProductPrice(ProductPriceEntity entity, FernPrincipal principal, ProductPriceUpsertRequest request, boolean create) {
        entity.setProduct(productService.requireProduct(request.productId()));
        entity.setScopeType(request.scopeType());
        entity.setScopeId(request.scopeId());
        entity.setPriceType(request.priceType());
        entity.setCurrencyCode(request.currencyCode());
        entity.setPriceValue(request.priceValue());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        if (create) {
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
        }
        entity.setUpdatedByUserId(principal == null ? null : principal.userId());
    }

    private void publishPrice(FernPrincipal principal, ProductPriceEntity entity, ProductPriceResponse before, ProductPriceResponse after) {
        catalogAuditService.publish("catalog.price.published", principal, before == null ? "PUBLISH_PRICE" : "UPDATE_PRICE", "product_price", String.valueOf(entity.getId()), before, after, Map.of("productId", entity.getProduct().getId()));
        catalogOutboxService.enqueue("product_price", String.valueOf(entity.getId()), "catalog.price.published", String.valueOf(entity.getProduct().getId()), after);
    }

    private TaxRateResponse toTaxRateResponse(TaxRateEntity entity) {
        return new TaxRateResponse(entity.getId(), entity.getProduct().getId(), entity.getTaxPercent(), entity.getEffectiveFrom(), entity.getEffectiveTo());
    }

    private ProductPriceResponse toProductPriceResponse(ProductPriceEntity entity) {
        return new ProductPriceResponse(
                entity.getId(),
                entity.getProduct().getId(),
                entity.getScopeType(),
                entity.getScopeId(),
                entity.getPriceType(),
                entity.getCurrencyCode(),
                entity.getPriceValue(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo()
        );
    }

    private ProductAvailabilityResponse toAvailabilityResponse(ProductOutletAvailabilityEntity entity) {
        return new ProductAvailabilityResponse(entity.getId().getProductId(), entity.getId().getOutletId(), entity.isAvailable());
    }
}
