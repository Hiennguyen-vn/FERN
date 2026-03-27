package com.fern.orgservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.orgservice.domain.OutletEntity;
import com.fern.orgservice.dto.CreateOutletRequest;
import com.fern.orgservice.dto.OutletResponse;
import com.fern.orgservice.dto.UpdateOutletRequest;
import com.fern.orgservice.repository.OutletRepository;
import com.fern.orgservice.repository.RegionRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutletService {
    private final OutletRepository outletRepository;
    private final RegionRepository regionRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final Clock clock;

    public OutletService(
            OutletRepository outletRepository,
            RegionRepository regionRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            Clock clock
    ) {
        this.outletRepository = outletRepository;
        this.regionRepository = regionRepository;
        this.orgAuthorizer = orgAuthorizer;
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
}
