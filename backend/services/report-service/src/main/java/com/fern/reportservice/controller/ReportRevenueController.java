package com.fern.reportservice.controller;

import com.fern.platform.common.FernPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/reports/revenue")
@Tag(name = "Report Revenue")
public class ReportRevenueController {
    private final RestClient restClient;
    private final String posBaseUrl;

    public ReportRevenueController(RestClient.Builder restClientBuilder, @Value("${fern.pos-base-url:http://localhost:8086}") String posBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.posBaseUrl = posBaseUrl;
    }

    @Operation(summary = "Get Report Revenue")
    @GetMapping("/outlet-stats/today")
    public List<Object> todayStats(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam List<Long> outletIds,
            HttpServletRequest request
    ) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) authHeader = "";

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme(java.net.URI.create(posBaseUrl).getScheme())
                        .host(java.net.URI.create(posBaseUrl).getHost())
                        .port(java.net.URI.create(posBaseUrl).getPort())
                        .path("/pos-stats/today")
                        .queryParam("outletIds", outletIds)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
