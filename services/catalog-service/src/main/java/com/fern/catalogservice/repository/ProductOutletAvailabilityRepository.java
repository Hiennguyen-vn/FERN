package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.ProductEntity;
import com.fern.catalogservice.domain.ProductOutletAvailabilityEntity;
import com.fern.catalogservice.domain.ProductOutletAvailabilityId;
import com.fern.catalogservice.domain.ProductStatus;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOutletAvailabilityRepository extends JpaRepository<ProductOutletAvailabilityEntity, ProductOutletAvailabilityId> {
    @EntityGraph(attributePaths = {"product"})
    List<ProductOutletAvailabilityEntity> findByIdOutletIdAndIsAvailableTrue(Long outletId);

    @EntityGraph(attributePaths = {"product"})
    List<ProductOutletAvailabilityEntity> findByIdProductIdOrderByIdOutletIdAsc(Long productId);

    @Query("""
            select product
            from ProductOutletAvailabilityEntity availability
            join availability.product product
            where availability.id.outletId = :outletId
              and availability.isAvailable = true
              and product.deletedAt is null
              and product.status = :status
            order by product.code
            """)
    List<ProductEntity> findAvailableProducts(
            @Param("outletId") Long outletId,
            @Param("status") ProductStatus status
    );
}
