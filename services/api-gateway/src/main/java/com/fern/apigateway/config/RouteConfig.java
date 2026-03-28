package com.fern.apigateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayRoutesProperties.class)
public class RouteConfig {
    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayRoutesProperties properties) {
        return builder.routes()
                .route("iam-auth", r -> r.path("/auth/**").uri(properties.getIam()))
                .route("iam-admin", r -> r.path("/users/**", "/roles/**", "/permissions/**").uri(properties.getIam()))
                .route("org-core", r -> r.path("/regions/**", "/outlets/**").uri(properties.getOrg()))
                .route("catalog-core", r -> r.path(
                        "/internal/catalog/**",
                        "/ingredients/**",
                        "/ingredient-categories/**",
                        "/product-categories/**",
                        "/units-of-measure/**",
                        "/uom-conversions/**",
                        "/products/**",
                        "/recipes/**",
                        "/recipe-versions/**",
                        "/tax-rates/**",
                        "/product-prices/**",
                        "/product-availability/**"
                ).uri(properties.getCatalog()))
                .route("audit-core", r -> r.path("/audit/**").uri(properties.getAudit()))
                .route("pos-core", r -> r.path("/pos-sessions/**", "/sale-orders/**").uri(properties.getPos()))
                .route("inventory-core", r -> r.path(
                        "/stock-balances/**",
                        "/inventory-transactions/**",
                        "/stock-adjustments/**",
                        "/waste-records/**",
                        "/stock-count-sessions/**"
                ).uri(properties.getInventory()))
                .route("procurement-core", r -> r.path(
                        "/suppliers/**",
                        "/purchase-orders/**",
                        "/goods-receipts/**",
                        "/supplier-invoices/**",
                        "/supplier-payments/**"
                ).uri(properties.getProcurement()))
                .route("hr-core", r -> r.path(
                        "/employees/**",
                        "/employee-contracts/**",
                        "/employee-assignments/**",
                        "/shift-schedules/**",
                        "/shift-assignments/**",
                        "/attendance-events/**",
                        "/attendance-approvals/**"
                ).uri(properties.getHr()))
                .route("finance-core", r -> r.path(
                        "/payroll-periods/**",
                        "/payroll-runs/**",
                        "/finance-config/**"
                ).uri(properties.getFinance()))
                .route("report-core", r -> r.path("/reports/**").uri(properties.getReport()))
                .build();
    }
}
