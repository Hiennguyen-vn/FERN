package com.fern.orgservice.controller;

import com.fern.orgservice.dto.ExchangeRateResponse;
import com.fern.orgservice.dto.UpsertExchangeRateRequest;
import com.fern.orgservice.service.ExchangeRateService;
import com.fern.platform.common.FernPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchange-rates")
public class ExchangeRateController {
    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    public List<ExchangeRateResponse> list(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) String fromCurrency,
            @RequestParam(required = false) String toCurrency,
            @RequestParam(required = false) LocalDate asOfDate
    ) {
        return exchangeRateService.list(principal, fromCurrency, toCurrency, asOfDate);
    }

    @PostMapping
    public ExchangeRateResponse upsert(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody UpsertExchangeRateRequest request
    ) {
        return exchangeRateService.upsert(principal, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam String fromCurrency,
            @RequestParam String toCurrency,
            @RequestParam LocalDate effectiveFrom
    ) {
        exchangeRateService.delete(principal, fromCurrency, toCurrency, effectiveFrom);
    }
}
