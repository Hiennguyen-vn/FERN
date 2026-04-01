package com.fern.orgservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.orgservice.domain.RegionEntity;
import com.fern.orgservice.dto.CreateRegionRequest;
import com.fern.orgservice.dto.RegionResponse;
import com.fern.orgservice.dto.UpdateRegionRequest;
import com.fern.orgservice.repository.RegionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

@Service
public class RegionService {
    private final RegionRepository regionRepository;
    private final RegionClosureRepository closureRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeExpansionService scopeExpansionService;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final Clock clock;

    public RegionService(
            RegionRepository regionRepository,
            RegionClosureRepository closureRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeExpansionService scopeExpansionService,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            Clock clock
    ) {
        this.regionRepository = regionRepository;
        this.closureRepository = closureRepository;
        this.orgAuthorizer = orgAuthorizer;
        this.scopeExpansionService = scopeExpansionService;
        this.scopeVersionService = scopeVersionService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public RegionResponse create(FernPrincipal principal, CreateRegionRequest request) {
        if (request.parentRegionId() != null) {
            orgAuthorizer.requireRegionAccess(principal, request.parentRegionId(), "org.region.write");
            regionRepository.findById(request.parentRegionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent region not found: " + request.parentRegionId()));
        } else {
            orgAuthorizer.requirePermission(principal, "org.region.write");
        }

        if (regionRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("Region code already exists: " + request.code());
        }

        RegionEntity entity = new RegionEntity();
        entity.setCode(request.code());
        entity.setParentRegionId(request.parentRegionId());
        entity.setCurrencyCode(request.currencyCode());
        entity.setName(request.name());
        entity.setTaxCode(request.taxCode());
        entity.setTimezoneName(request.timezoneName());
        entity.setCreatedAt(clock.instant());
        entity.setUpdatedAt(clock.instant());
        regionRepository.save(entity);

        closureRepository.rebuild();
        scopeVersionService.bump();
        outboxService.enqueue("region", entity.getId().toString(), "org.region.changed", entity.getId().toString(), toResponse(entity));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegionResponse> list(FernPrincipal principal, String search, Integer page, Integer size) {
        orgAuthorizer.requirePermission(principal, "org.region.read");
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        int clampedSize = ListQueryDefaults.clampLimit(size);
        var expanded = principal.scopeRoots().system()
                ? null
                : scopeExpansionService.expand(principal.scopeRoots().regions(), principal.scopeRoots().outlets());

        List<RegionResponse> items = regionRepository.findAll(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))).stream()
                .filter(region -> expanded == null || expanded.regionIds().contains(region.getId()))
                .filter(region -> matchesSearch(region, normalizedSearch))
                .map(this::toResponse)
                .toList();

        return toPageResponse(items, page, clampedSize);
    }

    @Transactional(readOnly = true)
    public RegionResponse get(FernPrincipal principal, Long id) {
        orgAuthorizer.requireRegionAccess(principal, id, "org.region.read");
        return toResponse(regionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + id)));
    }

    @Transactional
    public RegionResponse update(FernPrincipal principal, Long id, UpdateRegionRequest request) {
        RegionEntity entity = regionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + id));
        orgAuthorizer.requireRegionAccess(principal, id, "org.region.write");

        if (request.parentRegionId() != null && !request.parentRegionId().equals(entity.getParentRegionId())) {
            if (request.parentRegionId().equals(id) || closureRepository.isDescendant(id, request.parentRegionId())) {
                throw new ConflictException("Region hierarchy would become cyclic");
            }
            orgAuthorizer.requireRegionAccess(principal, request.parentRegionId(), "org.region.write");
            regionRepository.findById(request.parentRegionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent region not found: " + request.parentRegionId()));
            entity.setParentRegionId(request.parentRegionId());
        }
        if (request.currencyCode() != null) {
            entity.setCurrencyCode(request.currencyCode());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.taxCode() != null) {
            entity.setTaxCode(request.taxCode());
        }
        if (request.timezoneName() != null) {
            entity.setTimezoneName(request.timezoneName());
        }
        entity.setUpdatedAt(clock.instant());

        closureRepository.rebuild();
        scopeVersionService.bump();
        outboxService.enqueue("region", entity.getId().toString(), "org.region.changed", entity.getId().toString(), toResponse(entity));
        return toResponse(entity);
    }

    private RegionResponse toResponse(RegionEntity entity) {
        return new RegionResponse(
                entity.getId(),
                entity.getCode(),
                entity.getParentRegionId(),
                entity.getCurrencyCode(),
                entity.getName(),
                entity.getTaxCode(),
                entity.getTimezoneName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean matchesSearch(RegionEntity entity, String normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            return true;
        }
        return contains(entity.getId(), normalizedSearch)
                || contains(entity.getCode(), normalizedSearch)
                || contains(entity.getName(), normalizedSearch)
                || contains(entity.getCurrencyCode(), normalizedSearch)
                || contains(entity.getTaxCode(), normalizedSearch)
                || contains(entity.getTimezoneName(), normalizedSearch);
    }

    private boolean contains(Object value, String normalizedSearch) {
        return value != null && String.valueOf(value).toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private <T> PageResponse<T> toPageResponse(List<T> items, Integer page, int size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int offset = Math.toIntExact(ListQueryDefaults.offsetFrom(page, size));
        if (offset >= items.size()) {
            return new PageResponse<>(List.of(), safePage, size, false);
        }
        int endExclusive = Math.min(items.size(), offset + size + 1);
        List<T> window = items.subList(offset, endExclusive);
        boolean hasMore = window.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(window.subList(0, size)) : List.copyOf(window);
        return new PageResponse<>(pagedItems, safePage, size, hasMore);
    }
}
