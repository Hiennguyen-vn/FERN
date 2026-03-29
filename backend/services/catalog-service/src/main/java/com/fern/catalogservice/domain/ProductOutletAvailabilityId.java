package com.fern.catalogservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProductOutletAvailabilityId implements Serializable {
    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long outletId;

    public ProductOutletAvailabilityId() {
    }

    public ProductOutletAvailabilityId(Long productId, Long outletId) {
        this.productId = productId;
        this.outletId = outletId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOutletId() {
        return outletId;
    }

    public void setOutletId(Long outletId) {
        this.outletId = outletId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ProductOutletAvailabilityId that)) {
            return false;
        }
        return Objects.equals(productId, that.productId) && Objects.equals(outletId, that.outletId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, outletId);
    }
}
