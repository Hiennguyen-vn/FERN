package com.fern.orgservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.orgservice.domain.RegionEntity;
import com.fern.orgservice.dto.CreateRegionRequest;
import com.fern.orgservice.dto.RegionResponse;
import com.fern.orgservice.dto.UpdateRegionRequest;
import com.fern.orgservice.repository.RegionRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionService {
    private final RegionRepository regionRepository;
    private final RegionClosureRepository closureRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final Clock clock;

    public RegionService(
            RegionRepository regionRepository,
            RegionClosureRepository closureRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            Clock clock
    ) {
        this.regionRepository = regionRepository;
        this.closureRepository = closureRepository;
        this.orgAuthorizer = orgAuthorizer;
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
}
