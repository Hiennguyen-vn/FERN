package com.fern.orgservice.service;

import com.fern.platform.common.ConflictException;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.platform.common.PageResponse;
import com.fern.platform.common.ResourceNotFoundException;
import com.fern.orgservice.domain.OutletEntity;
import com.fern.orgservice.domain.OutletStatus;
import com.fern.orgservice.dto.CreateOutletRequest;
import com.fern.orgservice.dto.OutletResponse;
import com.fern.orgservice.dto.UpdateOutletRequest;
import com.fern.orgservice.repository.OutletRepository;
import com.fern.orgservice.repository.RegionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OutletService {
    private final OutletRepository outletRepository;
    private final RegionRepository regionRepository;
    private final OrgAuthorizer orgAuthorizer;
    private final ScopeExpansionService scopeExpansionService;
    private final ScopeVersionService scopeVersionService;
    private final OrgOutboxService outboxService;
    private final OrgPosClient orgPosClient;
    private final OrgInventoryClient orgInventoryClient;
    private final OrgProcurementClient orgProcurementClient;
    private final OrgFinanceClient orgFinanceClient;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public OutletService(
            OutletRepository outletRepository,
            RegionRepository regionRepository,
            OrgAuthorizer orgAuthorizer,
            ScopeExpansionService scopeExpansionService,
            ScopeVersionService scopeVersionService,
            OrgOutboxService outboxService,
            OrgPosClient orgPosClient,
            OrgInventoryClient orgInventoryClient,
            OrgProcurementClient orgProcurementClient,
            OrgFinanceClient orgFinanceClient,
            NamedParameterJdbcTemplate jdbcTemplate,
            Clock clock,
            TransactionTemplate transactionTemplate
    ) {
        this.outletRepository = outletRepository;
        this.regionRepository = regionRepository;
        this.orgAuthorizer = orgAuthorizer;
        this.scopeExpansionService = scopeExpansionService;
        this.scopeVersionService = scopeVersionService;
        this.outboxService = outboxService;
        this.orgPosClient = orgPosClient;
        this.orgInventoryClient = orgInventoryClient;
        this.orgProcurementClient = orgProcurementClient;
        this.orgFinanceClient = orgFinanceClient;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
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

    public OutletResponse update(FernPrincipal principal, Long id, UpdateOutletRequest request) {
        OutletEntity entity = outletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet not found: " + id));
        orgAuthorizer.requireOutletAccess(principal, id, "org.outlet.write");
        if (request.status() == OutletStatus.CLOSED) {
            if (entity.getStatus() == OutletStatus.CLOSED) {
                return toResponse(entity);
            }
            if (entity.getStatus() == OutletStatus.CLOSING) {
                throw new ConflictException("Outlet closure is already in progress");
            }
            return closeOutlet(principal, id, request, entity.getStatus(), entity.getClosedAt());
        }
        return Objects.requireNonNull(transactionTemplate.execute(status -> applyStandardUpdate(principal, id, request)));
    }

    private OutletResponse closeOutlet(
            FernPrincipal principal,
            Long outletId,
            UpdateOutletRequest request,
            OutletStatus originalStatus,
            LocalDate originalClosedAt
    ) {
        transactionTemplate.executeWithoutResult(status -> transitionOutletStatus(outletId, OutletStatus.CLOSING, originalClosedAt));
        try {
            ensureOutletCanBeClosed(principal, outletId);
            return Objects.requireNonNull(transactionTemplate.execute(status -> applyClosingUpdate(principal, outletId, request)));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> transitionOutletStatus(outletId, originalStatus, originalClosedAt));
            throw exception;
        }
    }

    @Transactional
    protected OutletResponse applyStandardUpdate(FernPrincipal principal, Long id, UpdateOutletRequest request) {
        OutletEntity entity = outletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet not found: " + id));
        applyMutableFields(principal, entity, request);
        long newVersion = scopeVersionService.bump();
        outboxService.enqueue("outlet", entity.getId().toString(), "org.outlet.changed", entity.getId().toString(), toResponse(entity));
        return withVersion(entity, newVersion);
    }

    @Transactional
    protected OutletResponse applyClosingUpdate(FernPrincipal principal, Long id, UpdateOutletRequest request) {
        OutletEntity entity = outletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet not found: " + id));
        applyMutableFields(principal, entity, request);
        entity.setStatus(OutletStatus.CLOSED);
        entity.setUpdatedAt(clock.instant());
        long newVersion = scopeVersionService.bump();
        outboxService.enqueue("outlet", entity.getId().toString(), "org.outlet.changed", entity.getId().toString(), toResponse(entity));
        return withVersion(entity, newVersion);
    }

    @Transactional
    protected void transitionOutletStatus(Long outletId, OutletStatus status, LocalDate closedAt) {
        OutletEntity entity = outletRepository.findById(outletId)
                .orElseThrow(() -> new ResourceNotFoundException("Outlet not found: " + outletId));
        entity.setStatus(status);
        entity.setClosedAt(closedAt);
        entity.setUpdatedAt(clock.instant());
        scopeVersionService.bump();
        outboxService.enqueue("outlet", entity.getId().toString(), "org.outlet.changed", entity.getId().toString(), toResponse(entity));
    }

    private void applyMutableFields(FernPrincipal principal, OutletEntity entity, UpdateOutletRequest request) {
        if (request.regionId() != null) {
            orgAuthorizer.requireRegionAccess(principal, request.regionId(), "org.outlet.write");
            regionRepository.findById(request.regionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Region not found: " + request.regionId()));
            entity.setRegionId(request.regionId());
        }
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.status() != null && request.status() != OutletStatus.CLOSED) {
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
    }

    private void ensureOutletCanBeClosed(FernPrincipal principal, Long outletId) {
        CompletableFuture<Boolean> posFuture =
                CompletableFuture.supplyAsync(() -> orgPosClient.hasOpenSessions(outletId, principal));
        CompletableFuture<OrgInventoryClient.OutletCloseCheck> inventoryFuture =
                CompletableFuture.supplyAsync(() -> orgInventoryClient.getOutletCloseCheck(outletId, principal));
        CompletableFuture<OrgProcurementClient.OutletCloseCheck> procurementFuture =
                CompletableFuture.supplyAsync(() -> orgProcurementClient.getOutletCloseCheck(outletId, principal));
        CompletableFuture<OrgFinanceClient.OutletCloseCheck> financeFuture =
                CompletableFuture.supplyAsync(() -> orgFinanceClient.getOutletCloseCheck(outletId, principal));
        try {
            CompletableFuture.allOf(posFuture, inventoryFuture, procurementFuture, financeFuture).join();
            if (posFuture.get()) {
                throw new ConflictException("Cannot close outlet while open POS sessions still exist");
            }
            OrgInventoryClient.OutletCloseCheck inventoryCheck = inventoryFuture.get();
            if (inventoryCheck.hasBlockingOperations()) {
                throw new ConflictException(
                        "Cannot close outlet while inventory workflows remain open: "
                                + "reservations=" + inventoryCheck.blockingReservations()
                                + ", stockCountSessions=" + inventoryCheck.blockingStockCountSessions()
                );
            }
            OrgProcurementClient.OutletCloseCheck procurementCheck = procurementFuture.get();
            if (procurementCheck.hasBlockingDocuments()) {
                throw new ConflictException(
                        "Cannot close outlet while procurement documents remain open: "
                                + "purchaseOrders=" + procurementCheck.blockingPurchaseOrders()
                                + ", goodsReceipts=" + procurementCheck.blockingGoodsReceipts()
                                + ", supplierInvoices=" + procurementCheck.blockingSupplierInvoices()
                );
            }
            OrgFinanceClient.OutletCloseCheck financeCheck = financeFuture.get();
            if (financeCheck.hasBlockingObligations()) {
                throw new ConflictException(
                        "Cannot close outlet while finance obligations remain open: payrollRuns=" + financeCheck.blockingPayrollRuns()
                );
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new com.fern.platform.common.DownstreamUnavailableException("Outlet close check failed", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.fern.platform.common.DownstreamUnavailableException("Outlet close check interrupted", e);
        }
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
