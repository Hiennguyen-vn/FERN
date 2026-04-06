package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.PriceScopeType;
import com.fern.catalogservice.domain.PriceType;
import com.fern.catalogservice.domain.ProductPriceEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductPriceRepository extends JpaRepository<ProductPriceEntity, Long> {
    @EntityGraph(attributePaths = {"product"})
    List<ProductPriceEntity> findAllByOrderByEffectiveFromDescIdDesc(Pageable pageable);

    @Query("""
            select (count(p) > 0)
            from ProductPriceEntity p
            where p.product.id = :productId
              and p.scopeType = :scopeType
              and ((:scopeId is null and p.scopeId is null) or p.scopeId = :scopeId)
              and p.priceType = :priceType
              and (:excludeId is null or p.id <> :excludeId)
              and p.effectiveFrom <= coalesce(:effectiveTo, p.effectiveFrom)
              and (p.effectiveTo is null or p.effectiveTo >= :effectiveFrom)
            """)
    boolean existsOverlap(
            @Param("productId") Long productId,
            @Param("scopeType") PriceScopeType scopeType,
            @Param("scopeId") Long scopeId,
            @Param("priceType") PriceType priceType,
            @Param("excludeId") Long excludeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );

    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select p
            from ProductPriceEntity p
            where p.product.id in :productIds
              and p.priceType = :priceType
              and p.effectiveFrom <= :businessDate
              and (p.effectiveTo is null or p.effectiveTo >= :businessDate)
            order by p.effectiveFrom desc, p.id desc
            """)
    List<ProductPriceEntity> findEffectivePrices(
            @Param("productIds") Collection<Long> productIds,
            @Param("priceType") PriceType priceType,
            @Param("businessDate") LocalDate businessDate
    );
}
