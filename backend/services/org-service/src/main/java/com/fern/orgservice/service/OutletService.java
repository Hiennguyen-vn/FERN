package com.fern.orgservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.orgservice.domain.OutletEntity;
import com.fern.orgservice.dto.CreateOutletRequest;
import com.fern.orgservice.dto.OutletResponse;
import com.fern.orgservice.dto.UpdateOutletRequest;
import com.fern.orgservice.repository.OutletRepository;
import com.fern.orgservice.repository.RegionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

@Service
public class OutletService {
    private final OutletRepository outletRepository;
    private final RegionRepository regionRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeExpansionService scopeExpansionService;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final Clock clock;

    public OutletService(
            OutletRepository outletRepository,
            RegionRepository regionRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeExpansionService scopeExpansionService,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            Clock clock
    ) {
        this.outletRepository = outletRepository;
        this.regionRepository = regionRepository;
        this.orgAuthorizer = orgAuthorizer;
        this.scopeExpansionService = scopeExpansionService;
        this.scopeVersionService = scopeVersionService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Transactional
    public OutletResponse create(FernPrincipal principal, CreateOutletRequest request) {
        orgAuthorizer.requireRegionAccess(principal, request.regionId(), "org.outlet.write");
        if (outletRepository.findByCode(request.code()).isPresent()) {
            throw new ConflictException("Outlet code already exists: " + request.code());
        }
        regionRepository.findById(request.regionId())
                .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + request.regionId()));

        OutletEntity entity = new OutletEntity();
        entity.setRegionId(request.regionId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setStatus(request.status());
        entity.setAddress(request.address());
        entity.setPhone(request.phone());
        entity.setEmail(request.email());
        entity.setOpenedAt(request.openedAt());
        entity.setClosedAt(request.closedAt());
        entity.setCreatedAt(clock.instant());
        entity.setUpdatedAt(clock.instant());

        outletRepository.save(entity);
        long newVersion = scopeVersionService.bump();
        outboxService.enqueue("outlet", entity.getId().toString(), "org.outlet.changed", entity.getId().toString(), toResponse(entity));
        return withVersion(entity, newVersion);
    }

    @Transactional(readOnly = true)
    public PageResponse<OutletResponse> list(FernPrincipal principal, Long regionId, String status, String search, Integer page, Integer size) {
        orgAuthorizer.requirePermission(principal, "org.outlet.read");
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedStatus = status == null ? null : status.trim();
        int clampedSize = ListQueryDefaults.clampLimit(size);
        var expanded = principal.scopeRoots().system()
                ? null
                : scopeExpansionService.expand(principal.scopeRoots().regions(), principal.scopeRoots().outlets());

        List<OutletResponse> items = outletRepository.findAll(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))).stream()
                .filter(outlet -> expanded == null || expanded.outletIds().contains(outlet.getId()))
                .filter(outlet -> regionId == null || regionId.equals(outlet.getRegionId()))
                .filter(outlet -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(outlet.getStatus().name()))
                .filter(outlet -> matchesSearch(outlet, normalizedSearch))
                .map(this::toResponse)
                .toList();

        return toPageResponse(items, page, clampedSize);
    }

    @Transactional(readOnly = true)
    public OutletResponse get(FernPrincipal principal, Long id) {
        orgAuthorizer.requireOutletAccess(principal, id, "org.outlet.read");
        return toResponse(outletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet not found: " + id)));
    }

    @Transactional
    public OutletResponse update(FernPrincipal principal, Long id, UpdateOutletRequest request) {
        OutletEntity entity = outletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet not found: " + id));
        orgAuthorizer.requireOutletAccess(principal, id, "org.outlet.write");

        if (request.regionId() != null) {
            orgAuthorizer.requireRegionAccess(principal, request.regionId(), "org.outlet.write");
            regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + request.regionId()));
            entity.setRegionId(request.regionId());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }
        if (request.address() != null) {
            entity.setAddress(request.address());
        }
        if (request.phone() != null) {
            entity.setPhone(request.phone());
        }
        if (request.email() != null) {
            entity.setEmail(request.email());
        }
        if (request.openedAt() != null) {
            entity.setOpenedAt(request.openedAt());
        }
        if (request.closedAt() != null) {
            entity.setClosedAt(request.closedAt());
        }
        entity.setUpdatedAt(clock.instant());

        long newVersion = scopeVersionService.bump();
        outboxService.enqueue("outlet", entity.getId().toString(), "org.outlet.changed", entity.getId().toString(), toResponse(entity));
        return withVersion(entity, newVersion);
    }

    private OutletResponse toResponse(OutletEntity entity) {
        return new OutletResponse(
                entity.getId(),
                entity.getRegionId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getAddress(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getOpenedAt(),
                entity.getClosedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private OutletResponse withVersion(OutletEntity entity, long ignoredVersion) {
        return toResponse(entity);
    }

    private boolean matchesSearch(OutletEntity entity, String normalizedSearch) {
        if (normalizedSearch.isBlank()) {
            return true;
        }
        return contains(entity.getId(), normalizedSearch)
                || contains(entity.getCode(), normalizedSearch)
                || contains(entity.getName(), normalizedSearch)
                || contains(entity.getStatus(), normalizedSearch)
                || contains(entity.getAddress(), normalizedSearch)
                || contains(entity.getPhone(), normalizedSearch)
                || contains(entity.getEmail(), normalizedSearch);
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
