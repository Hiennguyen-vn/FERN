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
                .route("org-core", r -> r.path("/regions/**", "/outlets/**", "/internal/scopes/**").uri(properties.getOrg()))
                .route("catalog-core", r -> r.path(
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
                        "/product-availability/**",
                        "/internal/catalog/**"
                ).uri(properties.getCatalog()))
                .route("audit-core", r -> r.path("/audit/**").uri(properties.getAudit()))
                .build();
    }
}
