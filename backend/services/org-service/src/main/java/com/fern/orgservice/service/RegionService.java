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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionService {
    private final RegionRepository regionRepository;
    private final RegionClosureRepository closureRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeExpansionService scopeExpansionService;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public RegionService(
            RegionRepository regionRepository,
            RegionClosureRepository closureRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeExpansionService scopeExpansionService,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            NamedParameterJdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.regionRepository = regionRepository;
        this.closureRepository = closureRepository;
        this.orgAuthorizer = orgAuthorizer;
        this.scopeExpansionService = scopeExpansionService;
        this.scopeVersionService = scopeVersionService;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
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
        int safePage = page == null || page < 0 ? 0 : page;
        int clampedSize = ListQueryDefaults.clampLimit(size);
        long offset = ListQueryDefaults.offsetFrom(safePage, clampedSize);
        var expanded = principal.scopeRoots().system()
                ? null
                : scopeExpansionService.expand(principal.scopeRoots().regions(), principal.scopeRoots().outlets());
        if (expanded != null && expanded.regionIds().isEmpty()) {
            return new PageResponse<>(List.of(), safePage, clampedSize, false);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, code, parent_region_id, currency_code, name, tax_code, timezone_name, created_at, updated_at
                FROM org.region
                WHERE 1 = 1
                """);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("limit", clampedSize + 1)
                .addValue("offset", offset);
        if (expanded != null) {
            sql.append(" AND id IN (:regionIds)");
            parameters.addValue("regionIds", expanded.regionIds());
        }
        if (!normalizedSearch.isBlank()) {
            sql.append("""
                     AND (
                        CAST(id AS text) ILIKE :search
                        OR code ILIKE :search
                        OR name ILIKE :search
                        OR currency_code ILIKE :search
                        OR COALESCE(tax_code, '') ILIKE :search
                        OR timezone_name ILIKE :search
                     )
                    """);
            parameters.addValue("search", "%" + normalizedSearch + "%");
        }
        sql.append("""
                 ORDER BY name ASC, id ASC
                 LIMIT :limit OFFSET :offset
                """);

        List<RegionResponse> items = jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> new RegionResponse(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getObject("parent_region_id") == null ? null : rs.getLong("parent_region_id"),
                rs.getString("currency_code"),
                rs.getString("name"),
                rs.getString("tax_code"),
                rs.getString("timezone_name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        ));

        return toPageResponseFromWindow(items, safePage, clampedSize);
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

    private <T> PageResponse<T> toPageResponseFromWindow(List<T> items, int page, int size) {
        boolean hasMore = items.size() > size;
        List<T> pagedItems = hasMore ? List.copyOf(items.subList(0, size)) : List.copyOf(items);
        return new PageResponse<>(pagedItems, page, size, hasMore);
    }
}
