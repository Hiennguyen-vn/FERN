package com.fern.reportservice.controller;

import com.fern.platform.common.FernPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports/inventory")
@Tag(name = "Report Inventory")
public class ReportInventoryController {
    private final RestClient restClient;
    private final String inventoryBaseUrl;

    public ReportInventoryController(RestClient.Builder restClientBuilder, @Value("${fern.inventory-base-url:http://localhost:8087}") String inventoryBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    @Operation(summary = "Get Report Inventory")
    @GetMapping("/stock-balances")
    public Object getStockBalances(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam MultiValueMap<String, String> allParams,
            HttpServletRequest request
    ) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) authHeader = "";

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(java.net.URI.create(inventoryBaseUrl).getScheme())
                        .host(java.net.URI.create(inventoryBaseUrl).getHost())
                        .port(java.net.URI.create(inventoryBaseUrl).getPort())
                        .path("/stock-balances")
                        .queryParams(allParams)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .body(Object.class);
    }

    @Operation(summary = "Get Report Inventory")
    @GetMapping("/inventory-transactions")
    public Object getInventoryTransactions(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam MultiValueMap<String, String> allParams,
            HttpServletRequest request
    ) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) authHeader = "";

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(java.net.URI.create(inventoryBaseUrl).getScheme())
                        .host(java.net.URI.create(inventoryBaseUrl).getHost())
                        .port(java.net.URI.create(inventoryBaseUrl).getPort())
                        .path("/inventory-transactions")
                        .queryParams(allParams)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .body(Object.class);
    }
}
