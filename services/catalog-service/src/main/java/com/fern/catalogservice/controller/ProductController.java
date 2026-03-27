package com.fern.catalogservice.controller;

import com.fern.catalogservice.dto.ProductResponse;
import com.fern.catalogservice.dto.ProductUpsertRequest;
import com.fern.catalogservice.service.CatalogAuthorizer;
import com.fern.catalogservice.service.ProductService;
import com.fern.platform.common.FernPrincipal;
import com.fern.platform.common.PermissionCodes;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {
    private final CatalogAuthorizer catalogAuthorizer;
    private final ProductService productService;

    public ProductController(CatalogAuthorizer catalogAuthorizer, ProductService productService) {
        this.catalogAuthorizer = catalogAuthorizer;
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> list(@AuthenticationPrincipal FernPrincipal principal) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_READ);
        return productService.list();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@AuthenticationPrincipal FernPrincipal principal, @PathVariable Long id) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_READ);
        return productService.get(id);
    }

    @PostMapping
    public ProductResponse create(@AuthenticationPrincipal FernPrincipal principal, @Valid @RequestBody ProductUpsertRequest request) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_WRITE);
        return productService.create(principal, request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @AuthenticationPrincipal FernPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpsertRequest request
    ) {
        catalogAuthorizer.requirePermission(principal, PermissionCodes.CATALOG_PRODUCT_WRITE);
        return productService.update(principal, id, request);
    }
}
