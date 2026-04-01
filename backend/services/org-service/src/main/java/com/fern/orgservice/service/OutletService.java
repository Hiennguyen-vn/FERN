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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutletService {
    private final OutletRepository outletRepository;
    private final RegionRepository regionRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeExpansionService scopeExpansionService;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public OutletService(
            OutletRepository outletRepository,
            RegionRepository regionRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeExpansionService scopeExpansionService,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            NamedParameterJdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.outletRepository = outletRepository;
        this.regionRepository = regionRepository;
        this.orgAuthorizer = orgAuthorizer;
        this.scopeExpansionService = scopeExpansionService;
        this.scopeVersionService = scopeVersionService;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
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
        int safePage = page == null || page < 0 ? 0 : page;
        int clampedSize = ListQueryDefaults.clampLimit(size);
        long offset = ListQueryDefaults.offsetFrom(safePage, clampedSize);
        var expanded = principal.scopeRoots().system()
                ? null
                : scopeExpansionService.expand(principal.scopeRoots().regions(), principal.scopeRoots().outlets());
        if (expanded != null && expanded.outletIds().isEmpty()) {
            return new PageResponse<>(List.of(), safePage, clampedSize, false);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, region_id, code, name, status, address, phone, email, opened_at, closed_at, created_at, updated_at
                FROM org.outlet
                WHERE 1 = 1
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", clampedSize + 1)
                .addValue("offset", offset);
        if (expanded != null) {
            sql.append(" AND id IN (:outletIds)");
            parameters.addValue("outletIds", expanded.outletIds());
        }
        if (regionId != null) {
            sql.append(" AND region_id = :regionId");
            parameters.addValue("regionId", regionId);
        }
        if (normalizedStatus != null) {
            sql.append(" AND status = :status");
            parameters.addValue("status", normalizedStatus);
        }
        if (!normalizedSearch.isBlank()) {
            sql.append("""
                     AND (
                        CAST(id AS text) ILIKE :search
                        OR code ILIKE :search
                        OR name ILIKE :search
                        OR status ILIKE :search
                        OR COALESCE(address, '') ILIKE :search
                        OR COALESCE(phone, '') ILIKE :search
                        OR COALESCE(email, '') ILIKE :search
                     )
                    """);
            parameters.addValue("search", "%" + normalizedSearch + "%");
        }
        sql.append("""
                 ORDER BY name ASC, id ASC
                 LIMIT :limit OFFSET :offset
                """);

        List<OutletResponse> items = jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new OutletResponse(
                rs.getLong("id"),
                rs.getLong("region_id"),
                rs.getString("code"),
                rs.getString("name"),
                com.fern.orgservice.domain.OutletStatus.valueOf(rs.getString("status")),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getObject("opened_at", java.time.LocalDate.class),
                rs.getObject("closed_at", java.time.LocalDate.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        ));

        return toPageResponseFromWindow(items, safePage, clampedSize);
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

    private <T> PageResponse<T> toPageResponseFromWindow(List<T> items, int page, int size) {
        boolean hasMore = items.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(items.subList(0, size)) : List.copyOf(items);
        return new PageResponse<>(pagedItems, page, size, hasMore);
    }
}
