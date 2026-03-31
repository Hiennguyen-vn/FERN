package com.fern.apigateway.security;

import java.util.Map;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class GatewayRouteTargetServiceResolver {
    private static final Map<String, String> ROUTE_ID_TO_SERVICE = Map.ofEntries(
            Map.entry("iam-auth", "iam-service"),
            Map.entry("iam-admin", "iam-service"),
            Map.entry("org-core", "org-service"),
            Map.entry("catalog-core", "catalog-service"),
            Map.entry("audit-core", "audit-service"),
            Map.entry("pos-core", "pos-service"),
            Map.entry("inventory-core", "inventory-service"),
            Map.entry("procurement-core", "procurement-service"),
            Map.entry("hr-core", "hr-service"),
            Map.entry("finance-core", "finance-service"),
            Map.entry("report-core", "report-service")
    );

    public String resolve(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            return ROUTE_ID_TO_SERVICE.get(route.getId());
        }
        String path = exchange.getRequest().getPath().value();
        if (matchesPath(path, "/auth") || matchesPath(path, "/users") || matchesPath(path, "/roles") || matchesPath(path, "/permissions")) {
            return "iam-service";
        }
        if (matchesPath(path, "/regions") || matchesPath(path, "/outlets")) {
            return "org-service";
        }
        if (matchesPath(path, "/ingredients")
                || matchesPath(path, "/ingredient-categories")
                || matchesPath(path, "/product-categories")
                || matchesPath(path, "/units-of-measure")
                || matchesPath(path, "/uom-conversions")
                || matchesPath(path, "/products")
                || matchesPath(path, "/recipes")
                || matchesPath(path, "/recipe-versions")
                || matchesPath(path, "/tax-rates")
                || matchesPath(path, "/product-prices")
                || matchesPath(path, "/product-availability")
                || matchesPath(path, "/catalog/promotions")) {
            return "catalog-service";
        }
        if (matchesPath(path, "/audit")) {
            return "audit-service";
        }
        if (matchesPath(path, "/pos-sessions") || matchesPath(path, "/sale-orders")) {
            return "pos-service";
        }
        if (matchesPath(path, "/stock-balances")
                || matchesPath(path, "/inventory-transactions")
                || matchesPath(path, "/stock-adjustments")
                || matchesPath(path, "/waste-records")
                || matchesPath(path, "/stock-count-sessions")) {
            return "inventory-service";
        }
        if (matchesPath(path, "/suppliers")
                || matchesPath(path, "/purchase-orders")
                || matchesPath(path, "/goods-receipts")
                || matchesPath(path, "/supplier-invoices")
                || matchesPath(path, "/supplier-payments")) {
            return "procurement-service";
        }
        if (matchesPath(path, "/employees")
                || matchesPath(path, "/employee-contracts")
                || matchesPath(path, "/employee-assignments")
                || matchesPath(path, "/shift-schedules")
                || matchesPath(path, "/shift-assignments")
                || matchesPath(path, "/attendance-events")
                || matchesPath(path, "/attendance-approvals")) {
            return "hr-service";
        }
        if (matchesPath(path, "/payroll-periods")
                || matchesPath(path, "/payroll-runs")
                || matchesPath(path, "/finance-config")) {
            return "finance-service";
        }
        if (matchesPath(path, "/reports")) {
            return "report-service";
        }
        return null;
    }

    private boolean matchesPath(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
