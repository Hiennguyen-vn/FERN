package com.fern.posservice.controller;

import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.ListQueryDefaults;
import com.fern.posservice.dto.PosCommands.CreateCustomerRequest;
import com.fern.posservice.dto.PosCommands.UpdateCustomerRequest;
import com.fern.posservice.dto.PosResponses.CustomerResponse;
import com.fern.posservice.dto.PosResponses.LoyaltyTransactionResponse;
import com.fern.posservice.service.PosCustomerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/customers")
@Tag(name = "Customer")
public class CustomerController {
    private final PosCustomerService customerService;

    public CustomerController(PosCustomerService customerService) {
        this.customerService = customerService;
    }

    @Operation(summary = "Create a new customer")
    @PostMapping
    public CustomerResponse createCustomer(
            @AuthenticationPrincipal FernPrincipal principal,
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        return customerService.createCustomer(principal, request);
    }

    @Operation(summary = "Get customer by ID")
    @GetMapping("/{id}")
    public CustomerResponse getCustomer(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id
    ) {
        return customerService.getCustomer(principal, id);
    }

    @Operation(summary = "Update a customer")
    @PatchMapping("/{id}")
    public CustomerResponse updateCustomer(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return customerService.updateCustomer(principal, id, request);
    }

    @Operation(summary = "Search customers by name, phone, code, or email")
    @GetMapping
    public List<CustomerResponse> searchCustomers(
            @AuthenticationPrincipal FernPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loyaltyTier,
            @RequestParam(required = false) Integer limit
    ) {
        int clampedLimit = ListQueryDefaults.clampLimit(limit);
        if (q != null && !q.isBlank()) {
            return customerService.searchCustomers(principal, q, clampedLimit);
        }
        return customerService.listCustomers(principal, status, loyaltyTier, clampedLimit);
    }

    @Operation(summary = "Get loyalty transaction history for a customer")
    @GetMapping("/{id}/loyalty-transactions")
    public List<LoyaltyTransactionResponse> getLoyaltyTransactions(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit
    ) {
        return customerService.getLoyaltyTransactions(principal, id, ListQueryDefaults.clampLimit(limit));
    }
}
