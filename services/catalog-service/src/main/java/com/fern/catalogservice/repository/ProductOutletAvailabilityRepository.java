package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.ProductOutletAvailabilityEntity;
import com.fern.catalogservice.domain.ProductOutletAvailabilityId;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductOutletAvailabilityRepository extends JpaRepository<ProductOutletAvailabilityEntity, ProductOutletAvailabilityId> {
    @EntityGraph(attributePaths = {"product"})
    List<ProductOutletAvailabilityEntity> findByIdOutletIdAndIsAvailableTrue(Long outletId);

    @EntityGraph(attributePaths = {"product"})
    List<ProductOutletAvailabilityEntity> findByIdProductIdOrderByIdOutletIdAsc(Long productId);
}
