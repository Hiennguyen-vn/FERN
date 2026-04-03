package com.fern.apigateway.ui;

import com.fern.platform.security.FernJwtClaims;
import com.fern.platform.security.FernJwtService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ui")
public class UiSurfaceController {
    private final FernJwtService jwtService;

    private static final List<UiModuleEntry> MODULES = List.of(
            new UiModuleEntry("/home", "Home", "Role-aware command surface for the current operator."),
            new UiModuleEntry("/pos", "POS", "Live terminal, payments, and session controls."),
            new UiModuleEntry("/catalog", "Catalog", "Products, ingredients, recipes, pricing, and availability."),
            new UiModuleEntry("/iam", "IAM", "Users, assignments, permission scopes, and effective access."),
            new UiModuleEntry("/audit", "Audit", "Audit events, security events, and request traces."),
            new UiModuleEntry("/org", "Org", "Regions, outlets, and configuration governance."),
            new UiModuleEntry("/regional-ops", "Regional Ops", "Regional dashboards and outlet oversight."),
            new UiModuleEntry("/hr", "HR", "Employees, contracts, attendance, and payroll preparation."),
            new UiModuleEntry("/finance", "Finance", "Suppliers, payroll approvals, and finance configuration."),
            new UiModuleEntry("/procurement", "Procurement", "Purchase orders, receipts, invoices, and payments."),
            new UiModuleEntry("/inventory", "Inventory", "Balances, counts, waste, and stock adjustments."),
            new UiModuleEntry("/workforce", "Workforce", "Attendance review and self-service workflows."),
            new UiModuleEntry("/reports", "Reports", "Analytics dashboards and export jobs.")
    );

    public UiSurfaceController(FernJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping("/action-hub")
    public Mono<ActionHubResponse> actionHub(
            ServerWebExchange exchange,
            @RequestParam(required = false) Long selectedRegionId,
            @RequestParam(required = false) Long selectedOutletId
    ) {
        UiPrincipal principal = UiPrincipal.from(exchange, jwtService);
        UiSelection selection = resolveSelection(principal, selectedRegionId, selectedOutletId);
        UiPersona persona = resolvePersona(principal);
        List<UiModuleEntry> visibleModules = visibleModules(principal);
        List<UiQuickAction> quickActions = quickActions(principal);

        return Mono.just(new ActionHubResponse(
                persona.name().toLowerCase(),
                new UiScopeSummary(
                        selection.hasFocusedScope() ? "Focused operating context" : principal.systemScope() ? "Full enterprise coverage" : "Scoped operating context",
                        selection.hasFocusedScope()
                                ? "Action surfaces are aligned to the currently selected frontend scope."
                                : principal.systemScope()
                                        ? "System-wide action surfaces are available to the current role."
                                        : "Select the correct region or outlet in the frontend shell before executing live workflows.",
                        scopeChips(principal, selection)
                ),
                List.of(
                        new UiKpiCard("coverage", "Coverage", principal.systemScope() ? "Enterprise" : visibleModules.size() + " modules", "primary", "Published workspaces available now"),
                        new UiKpiCard("regions", "Regional span", String.valueOf(principal.regions().size()), "success", "Regions visible to the current principal"),
                        new UiKpiCard("outlets", "Outlet span", String.valueOf(principal.outlets().size()), "warning", "Outlets visible to the current principal"),
                        new UiKpiCard("actions", "Ready actions", String.valueOf(quickActions.size()), "neutral", "Role-aware shortcuts exposed in the action hub")
                ),
                queues(principal),
                alerts(principal),
                quickActions,
                visibleModules
        ));
    }

    @GetMapping("/shell-context")
    public Mono<ShellContextResponse> shellContext(
            ServerWebExchange exchange,
            @RequestParam(required = false) Long selectedRegionId,
            @RequestParam(required = false) Long selectedOutletId
    ) {
        UiPrincipal principal = UiPrincipal.from(exchange, jwtService);
        UiSelection selection = resolveSelection(principal, selectedRegionId, selectedOutletId);
        UiPersona persona = resolvePersona(principal);

        return Mono.just(new ShellContextResponse(
                principal.username(),
                roleLabel(principal, persona),
                scopeChips(principal, selection),
                principal.regions().stream().map(regionId -> new UiScopeOption(regionId, "Region #" + regionId)).toList(),
                principal.outlets().stream().map(outletId -> new UiScopeOption(outletId, "Outlet #" + outletId)).toList()
        ));
    }

    private UiPersona resolvePersona(UiPrincipal principal) {
        if (principal.systemScope() || principal.roles().stream().anyMatch(role -> containsAnyIgnoreCase(role, "admin", "system", "bootstrap"))) {
            return UiPersona.SYSTEM_ADMIN;
        }
        if (principal.permissions().stream().anyMatch(permission -> permission.startsWith("finance.") || permission.startsWith("procurement.invoice") || permission.startsWith("procurement.payment"))) {
            return UiPersona.FINANCE;
        }
        if (principal.permissions().stream().anyMatch(permission -> permission.startsWith("pos.") || permission.startsWith("inventory.") || permission.startsWith("procurement.purchaseOrder") || permission.startsWith("procurement.goodsReceipt"))) {
            return UiPersona.OUTLET_MANAGER;
        }
        if (principal.permissions().stream().anyMatch(permission -> permission.startsWith("hr.attendance") || permission.startsWith("hr.shift"))) {
            return UiPersona.STAFF;
        }
        return UiPersona.OPERATIONS;
    }

    private String roleLabel(UiPrincipal principal, UiPersona persona) {
        if (!principal.roles().isEmpty()) {
            return titleCase(principal.roles().get(0));
        }
        return switch (persona) {
            case SYSTEM_ADMIN -> "System Admin";
            case FINANCE -> "Finance Lead";
            case OUTLET_MANAGER -> "Outlet Operations";
            case STAFF -> "Self-Service Staff";
            case OPERATIONS -> "Operations User";
        };
    }

    private UiSelection resolveSelection(UiPrincipal principal, Long selectedRegionId, Long selectedOutletId) {
        Long resolvedRegionId = inRegionScope(principal, selectedRegionId) ? selectedRegionId : null;
        Long resolvedOutletId = inOutletScope(principal, selectedOutletId) ? selectedOutletId : null;
        return new UiSelection(resolvedRegionId, resolvedOutletId);
    }

    private boolean inRegionScope(UiPrincipal principal, Long regionId) {
        return regionId != null && (principal.systemScope() || principal.regions().contains(regionId));
    }

    private boolean inOutletScope(UiPrincipal principal, Long outletId) {
        return outletId != null && (principal.systemScope() || principal.outlets().contains(outletId));
    }

    private List<String> scopeChips(UiPrincipal principal, UiSelection selection) {
        List<String> chips = new ArrayList<>();
        if (principal.systemScope()) {
            chips.add("Enterprise");
        }
        chips.add(selection.selectedRegionId() != null
                ? "Region #" + selection.selectedRegionId()
                : principal.regions().isEmpty() ? "No region" : principal.regions().size() + " regions");
        chips.add(selection.selectedOutletId() != null
                ? "Outlet #" + selection.selectedOutletId()
                : principal.outlets().isEmpty() ? "No outlet" : principal.outlets().size() + " outlets");
        return chips;
    }

    private List<UiModuleEntry> visibleModules(UiPrincipal principal) {
        return MODULES.stream()
                .filter(module -> module.href().equals("/home") || isModuleVisible(principal, module.href()))
                .toList();
    }

    private boolean isModuleVisible(UiPrincipal principal, String modulePath) {
        return switch (modulePath) {
            case "/pos" -> hasPermissionPrefix(principal, "pos.");
            case "/catalog" -> hasPermissionPrefix(principal, "catalog.");
            case "/iam" -> hasPermissionPrefix(principal, "iam.");
            case "/audit" -> hasPermissionPrefix(principal, "audit.");
            case "/org", "/regional-ops" -> hasPermissionPrefix(principal, "org.");
            case "/hr" -> hasPermissionPrefix(principal, "hr.") || hasPermissionPrefix(principal, "finance.payroll");
            case "/finance" -> hasPermissionPrefix(principal, "finance.") || hasPermissionPrefix(principal, "procurement.payment") || hasPermissionPrefix(principal, "procurement.invoice");
            case "/procurement" -> hasPermissionPrefix(principal, "procurement.purchaseOrder") || hasPermissionPrefix(principal, "procurement.goodsReceipt") || hasPermissionPrefix(principal, "procurement.supplier");
            case "/inventory" -> hasPermissionPrefix(principal, "inventory.");
            case "/workforce" -> hasPermissionPrefix(principal, "hr.attendance");
            case "/reports" -> hasPermissionPrefix(principal, "reports.") || hasPermissionPrefix(principal, "report.");
            default -> false;
        };
    }

    private boolean hasPermissionPrefix(UiPrincipal principal, String prefix) {
        return principal.permissions().stream().anyMatch(permission -> permission.startsWith(prefix));
    }

    private List<UiQuickAction> quickActions(UiPrincipal principal) {
        List<UiQuickAction> actions = new ArrayList<>();
        if (isModuleVisible(principal, "/pos")) {
            actions.add(new UiQuickAction("open-pos", "Open POS workspace", "Launch the live terminal workspace, payment flows, and session controls.", "/pos", "primary"));
        }
        if (isModuleVisible(principal, "/procurement")) {
            actions.add(new UiQuickAction("create-po", "Create purchase order", "Start a procurement flow aligned to the active operating scope.", "/procurement", "warning"));
        }
        if (isModuleVisible(principal, "/inventory")) {
            actions.add(new UiQuickAction("check-stock", "Check stock balances", "Review live balances before ordering or resolving stock exceptions.", "/inventory", "success"));
        }
        if (isModuleVisible(principal, "/workforce")) {
            actions.add(new UiQuickAction("review-attendance", "Review attendance", "Open the attendance approval queue for workforce exceptions.", "/workforce", "neutral"));
        }
        if (isModuleVisible(principal, "/reports")) {
            actions.add(new UiQuickAction("open-reports", "Open reports center", "Move into analytics, exports, and executive dashboards.", "/reports", "neutral"));
        }
        if (isModuleVisible(principal, "/audit")) {
            actions.add(new UiQuickAction("investigate-audit", "Investigate audit trail", "Inspect audit events and request traces for operational or security investigations.", "/audit/events", "danger"));
        }
        if (isModuleVisible(principal, "/regional-ops")) {
            actions.add(new UiQuickAction("regional-outlets", "Review regional outlets", "Scan outlet readiness, anomalies, and escalation signals.", "/regional-ops/outlets", "neutral"));
        }
        if (isModuleVisible(principal, "/hr")) {
            actions.add(new UiQuickAction("prepare-payroll", "Prepare payroll draft", "Move from attendance outcomes into payroll preparation.", "/hr/payroll-preparation", "primary"));
        }
        if (isModuleVisible(principal, "/iam")) {
            actions.add(new UiQuickAction("open-iam", "Open IAM console", "Inspect user assignments, scoped permissions, and effective access posture.", "/iam", "neutral"));
        }
        if (isModuleVisible(principal, "/finance")) {
            actions.add(new UiQuickAction("review-payroll-approvals", "Review payroll approvals", "Inspect payroll runs waiting for finance sign-off.", "/finance", "warning"));
            actions.add(new UiQuickAction("browse-suppliers", "Browse suppliers", "Open supplier master data and payment context.", "/finance/suppliers", "neutral"));
            actions.add(new UiQuickAction("manage-payroll-periods", "Manage payroll periods", "Create and advance payroll periods from the finance workspace.", "/finance/payroll-periods", "neutral"));
        }
        return actions;
    }

    private List<UiQueueCard> queues(UiPrincipal principal) {
        List<UiQueueCard> queues = new ArrayList<>();
        if (isModuleVisible(principal, "/procurement")) {
            queues.add(new UiQueueCard("procurement", "Procurement approvals", 3, "Purchase orders, receipts, and invoice checkpoints waiting for action.", "/procurement", "warning"));
        }
        if (isModuleVisible(principal, "/inventory")) {
            queues.add(new UiQueueCard("inventory", "Inventory exceptions", 2, "Adjustments, waste, and count variances needing confirmation.", "/inventory", "success"));
        }
        if (isModuleVisible(principal, "/workforce")) {
            queues.add(new UiQueueCard("workforce", "Attendance review", 4, "Attendance records and schedule exceptions pending review.", "/workforce", "neutral"));
        }
        if (isModuleVisible(principal, "/finance")) {
            queues.add(new UiQueueCard("finance", "Finance decisions", 2, "Payroll and supplier payment items waiting for acknowledgement.", "/finance", "primary"));
        }
        if (isModuleVisible(principal, "/iam")) {
            queues.add(new UiQueueCard("iam", "Access governance", 1, "User access and policy review tasks surfaced for administrators.", "/iam", "danger"));
        }
        return queues;
    }

    private List<UiAlertCard> alerts(UiPrincipal principal) {
        List<UiAlertCard> alerts = new ArrayList<>();
        if (principal.regions().isEmpty() && principal.outlets().isEmpty() && !principal.systemScope()) {
            alerts.add(new UiAlertCard("scope", "Scope selection recommended", "Choose a region or outlet in the frontend shell before executing live workflows.", "warning", null));
        }
        if (principal.permissions().isEmpty()) {
            alerts.add(new UiAlertCard("permissions", "No published permissions", "This principal has no published UI permissions yet. Most workspaces remain hidden or read-only.", "danger", null));
        } else {
            alerts.add(new UiAlertCard("contracts", "Capability boundary respected", "Readonly and unpublished flows stay visually available but remain action-gated until backend contracts are published.", "neutral", null));
        }
        return alerts;
    }

    private boolean containsAnyIgnoreCase(String value, String... fragments) {
        String normalized = value == null ? "" : value.toLowerCase();
        for (String fragment : fragments) {
            if (normalized.contains(fragment.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String titleCase(String value) {
        return String.join(" ", value.split("[_\\s-]+"))
                .trim()
                .replaceAll("\\s+", " ")
                .transform(result -> {
                    String[] parts = result.split(" ");
                    List<String> normalized = new ArrayList<>();
                    for (String part : parts) {
                        if (!part.isBlank()) {
                            normalized.add(part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase());
                        }
                    }
                    return String.join(" ", normalized);
                });
    }

    record ActionHubResponse(
            String persona,
            UiScopeSummary scopeSummary,
            List<UiKpiCard> kpis,
            List<UiQueueCard> queues,
            List<UiAlertCard> alerts,
            List<UiQuickAction> quickActions,
            List<UiModuleEntry> modules
    ) {
    }

    record ShellContextResponse(
            String principalLabel,
            String roleLabel,
            List<String> scopeChips,
            List<UiScopeOption> availableRegions,
            List<UiScopeOption> availableOutlets
    ) {
    }

    record UiScopeSummary(String title, String subtitle, List<String> chips) {
    }

    record UiKpiCard(String id, String label, String value, String tone, String detail) {
    }

    record UiQueueCard(String id, String title, int count, String description, String href, String tone) {
    }

    record UiAlertCard(String id, String title, String message, String tone, String href) {
    }

    record UiQuickAction(String id, String title, String description, String href, String tone) {
    }

    record UiModuleEntry(String href, String title, String description) {
    }

    record UiScopeOption(Long value, String label) {
    }

    record UiSelection(Long selectedRegionId, Long selectedOutletId) {
        boolean hasFocusedScope() {
            return selectedRegionId != null || selectedOutletId != null;
        }
    }

    enum UiPersona {
        SYSTEM_ADMIN,
        FINANCE,
        OUTLET_MANAGER,
        STAFF,
        OPERATIONS
    }

    record UiPrincipal(
            String username,
            List<String> roles,
            List<String> permissions,
            boolean systemScope,
            List<Long> regions,
            List<Long> outlets
    ) {
        @SuppressWarnings("unchecked")
        static UiPrincipal from(ServerWebExchange exchange, FernJwtService jwtService) {
            HttpHeaders headers = exchange.getRequest().getHeaders();
            String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                try {
                    FernJwtClaims claims = jwtService.decode(authorization.substring(7));
                    return new UiPrincipal(
                            claims.username(),
                            claims.roles().stream().toList(),
                            claims.permissions().stream().toList(),
                            claims.accessibleScope().system(),
                            claims.accessibleScope().regions(),
                            claims.accessibleScope().outlets()
                    );
                } catch (Exception ignored) {
                    // Fall through to exchange attributes / headers.
                }
            }

            Object username = exchange.getAttribute(UiContextAttributeKeys.USERNAME);
            Object roles = exchange.getAttribute(UiContextAttributeKeys.ROLES);
            Object permissions = exchange.getAttribute(UiContextAttributeKeys.PERMISSIONS);
            Object scopeSystem = exchange.getAttribute(UiContextAttributeKeys.SCOPE_SYSTEM);
            Object scopeRegions = exchange.getAttribute(UiContextAttributeKeys.SCOPE_REGIONS);
            Object scopeOutlets = exchange.getAttribute(UiContextAttributeKeys.SCOPE_OUTLETS);

            return new UiPrincipal(
                    username instanceof String value ? value : header(headers, "X-Fern-Username", "guest"),
                    roles instanceof List<?> value ? value.stream().map(String::valueOf).toList() : split(headers.getFirst("X-Fern-Roles")),
                    permissions instanceof List<?> value ? value.stream().map(String::valueOf).toList() : split(headers.getFirst("X-Fern-Permissions")),
                    scopeSystem instanceof Boolean value ? value : Boolean.parseBoolean(header(headers, "X-Fern-Scope-System", "false")),
                    scopeRegions instanceof List<?> value ? value.stream().map(item -> Long.parseLong(String.valueOf(item))).toList() : splitLongs(headers.getFirst("X-Fern-Scope-Regions")),
                    scopeOutlets instanceof List<?> value ? value.stream().map(item -> Long.parseLong(String.valueOf(item))).toList() : splitLongs(headers.getFirst("X-Fern-Scope-Outlets"))
            );
        }

        private static String header(HttpHeaders headers, String name, String fallback) {
            String value = headers.getFirst(name);
            return value == null || value.isBlank() ? fallback : value;
        }

        private static List<String> split(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return List.of(value.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .toList();
        }

        private static List<Long> splitLongs(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return List.of(value.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .map(Long::parseLong)
                    .toList();
        }
    }
}
