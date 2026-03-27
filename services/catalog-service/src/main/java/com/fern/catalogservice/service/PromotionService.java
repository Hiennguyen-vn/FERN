package com.fern.catalogservice.service;

import com.fern.catalogservice.domain.PromotionEntity;
import com.fern.catalogservice.dto.PromotionResponse;
import com.fern.catalogservice.dto.PromotionUpsertRequest;
import com.fern.catalogservice.repository.PromotionRepository;
import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final PromotionRepository promotionRepository;
    private final CatalogAuditService catalogAuditService;
    private final CatalogOutboxService catalogOutboxService;

    public PromotionService(
            PromotionRepository promotionRepository,
            CatalogAuditService catalogAuditService,
            CatalogOutboxService catalogOutboxService
    ) {
        this.promotionRepository = promotionRepository;
        this.catalogAuditService = catalogAuditService;
        this.catalogOutboxService = catalogOutboxService;
    }

    @Transactional
    public PromotionResponse createPromotion(FernPrincipal principal, PromotionUpsertRequest request) {
        validateNoOverlap(request, null);
        PromotionEntity entity = new PromotionEntity();
        apply(entity, principal, request, true);
        entity.setStatus(STATUS_ACTIVE);
        entity = promotionRepository.save(entity);
        PromotionResponse response = toResponse(entity);
        publishChanged(principal, entity, null, response, "CREATE_PROMOTION");
        return response;
    }

    @Transactional
    public PromotionResponse updatePromotion(FernPrincipal principal, Long id, PromotionUpsertRequest request) {
        PromotionEntity entity = requirePromotion(id);
        validateNoOverlap(request, id);
        PromotionResponse before = toResponse(entity);
        apply(entity, principal, request, false);
        entity = promotionRepository.save(entity);
        PromotionResponse response = toResponse(entity);
        publishChanged(principal, entity, before, response, "UPDATE_PROMOTION");
        return response;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listPromotions(FernPrincipal principal, String scopeType, Long scopeId) {
        return promotionRepository.findAllByOrderByEffectiveFromDescIdDesc().stream()
                .filter(entity -> scopeType == null || entity.getScopeType().equalsIgnoreCase(scopeType))
                .filter(entity -> scopeId == null || (entity.getScopeId() != null && entity.getScopeId().equals(scopeId)))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PromotionResponse deactivatePromotion(FernPrincipal principal, Long id) {
        PromotionEntity entity = requirePromotion(id);
        PromotionResponse before = toResponse(entity);
        entity.setStatus(STATUS_INACTIVE);
        entity.setUpdatedByUserId(principal == null ? null : principal.userId());
        entity = promotionRepository.save(entity);
        PromotionResponse response = toResponse(entity);
        publishChanged(principal, entity, before, response, "DEACTIVATE_PROMOTION");
        return response;
    }

    @Transactional(readOnly = true)
    public Optional<PromotionResponse> resolveApplicablePromotion(
            String code,
            Long outletId,
            Long regionId,
            BigDecimal orderTotal,
            LocalDate businessDate
    ) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        LocalDate effectiveDate = businessDate == null ? LocalDate.now() : businessDate;
        return candidatePromotions(code, effectiveDate).stream()
                .filter(entity -> matchesScope(entity, outletId, regionId))
                .filter(entity -> meetsMinimumOrder(entity, orderTotal))
                .sorted((left, right) -> Integer.compare(scopeRank(left.getScopeType()), scopeRank(right.getScopeType())))
                .map(this::toResponse)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public PromotionEntity requirePromotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found"));
    }

    private List<PromotionEntity> candidatePromotions(String code, LocalDate businessDate) {
        return promotionRepository.findApplicableCandidates(code, STATUS_ACTIVE, businessDate);
    }

    private void apply(PromotionEntity entity, FernPrincipal principal, PromotionUpsertRequest request, boolean create) {
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setPromotionType(request.promotionType());
        entity.setDiscountPercent(request.discountPercent());
        entity.setDiscountAmount(request.discountAmount());
        entity.setScopeType(request.scopeType());
        entity.setScopeId(request.scopeId());
        entity.setMinOrderAmount(request.minOrderAmount());
        entity.setMaxUsageTotal(request.maxUsageTotal());
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setEffectiveTo(request.effectiveTo());
        if (create) {
            entity.setCreatedByUserId(principal == null ? null : principal.userId());
        }
        entity.setUpdatedByUserId(principal == null ? null : principal.userId());
    }

    private void validate(PromotionUpsertRequest request) {
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new ConflictException("Promotion effectiveTo must be on or after effectiveFrom");
        }
        if ("GLOBAL".equalsIgnoreCase(request.scopeType()) && request.scopeId() != null) {
            throw new ConflictException("GLOBAL promotion scope must not have a scopeId");
        }
        if (!"GLOBAL".equalsIgnoreCase(request.scopeType()) && request.scopeId() == null) {
            throw new ConflictException("Non-global promotion scope requires a scopeId");
        }
        if ((request.discountPercent() == null && request.discountAmount() == null)
                || (request.discountPercent() != null && request.discountAmount() != null)) {
            throw new ConflictException("Promotion must define either discountPercent or discountAmount");
        }
        if (request.discountPercent() != null && request.discountPercent().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ConflictException("discountPercent must be less than or equal to 100");
        }
    }

    private void validateNoOverlap(PromotionUpsertRequest request, Long excludeId) {
        validate(request);
        boolean overlap = promotionRepository.findAllByCodeIgnoreCaseOrderByEffectiveFromDescIdDesc(request.code()).stream()
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .filter(existing -> normalizeScope(existing.getScopeType()).equals(normalizeScope(request.scopeType())))
                .filter(existing -> sameScopeId(existing.getScopeId(), request.scopeId()))
                .anyMatch(existing -> EffectiveDateSupport.overlaps(
                        existing.getEffectiveFrom(),
                        existing.getEffectiveTo(),
                        request.effectiveFrom(),
                        request.effectiveTo()
                ));
        if (overlap) {
            throw new ConflictException("Promotion overlaps an existing effective period for the same code and scope");
        }
    }

    private boolean matchesScope(PromotionEntity entity, Long outletId, Long regionId) {
        return switch (normalizeScope(entity.getScopeType())) {
            case "GLOBAL" -> true;
            case "REGION" -> entity.getScopeId() != null && entity.getScopeId().equals(regionId);
            case "OUTLET" -> entity.getScopeId() != null && entity.getScopeId().equals(outletId);
            default -> false;
        };
    }

    private boolean meetsMinimumOrder(PromotionEntity entity, BigDecimal orderTotal) {
        return entity.getMinOrderAmount() == null
                || (orderTotal != null && orderTotal.compareTo(entity.getMinOrderAmount()) >= 0);
    }

    private int scopeRank(String scopeType) {
        return switch (normalizeScope(scopeType)) {
            case "OUTLET" -> 0;
            case "REGION" -> 1;
            case "GLOBAL" -> 2;
            default -> 3;
        };
    }

    private String normalizeScope(String scopeType) {
        return scopeType == null ? "" : scopeType.toUpperCase();
    }

    private boolean sameScopeId(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private void publishChanged(
            FernPrincipal principal,
            PromotionEntity entity,
            PromotionResponse before,
            PromotionResponse after,
            String action
    ) {
        catalogAuditService.publish(
                "catalog.promotion.changed",
                principal,
                promotionRegionId(entity),
                promotionOutletId(entity),
                action,
                "promotion",
                String.valueOf(entity.getId()),
                before,
                after,
                Map.of("code", entity.getCode(), "scopeType", entity.getScopeType())
        );
        catalogOutboxService.enqueue("promotion", String.valueOf(entity.getId()), "catalog.promotion.changed", entity.getCode(), after);
    }

    private Long promotionRegionId(PromotionEntity entity) {
        return "REGION".equalsIgnoreCase(entity.getScopeType()) ? entity.getScopeId() : null;
    }

    private Long promotionOutletId(PromotionEntity entity) {
        return "OUTLET".equalsIgnoreCase(entity.getScopeType()) ? entity.getScopeId() : null;
    }

    private PromotionResponse toResponse(PromotionEntity entity) {
        return new PromotionResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getPromotionType(),
                entity.getDiscountPercent(),
                entity.getDiscountAmount(),
                entity.getScopeType(),
                entity.getScopeId(),
                entity.getMinOrderAmount(),
                entity.getMaxUsageTotal(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getStatus(),
                entity.getCreatedByUserId(),
                entity.getUpdatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
