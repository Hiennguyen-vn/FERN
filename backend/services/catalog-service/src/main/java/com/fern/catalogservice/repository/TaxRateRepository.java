package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.TaxRateEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRateRepository extends JpaRepository<TaxRateEntity, Long> {
    @EntityGraph(attributePaths = {"product"})
    List<TaxRateEntity> findAllByOrderByEffectiveFromDescIdDesc();

    @Query("""
            select (count(t) > 0)
            from TaxRateEntity t
            where t.product.id = :productId
              and (:excludeId is null or t.id <> :excludeId)
              and t.effectiveFrom <= coalesce(:effectiveTo, t.effectiveFrom)
              and coalesce(t.effectiveTo, :effectiveTo) >= :effectiveFrom
            """)
    boolean existsOverlap(
            @Param("productId") Long productId,
            @Param("excludeId") Long excludeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );

    @EntityGraph(attributePaths = {"product"})
    @Query("""
            select t
            from TaxRateEntity t
            where t.product.id in :productIds
              and t.effectiveFrom <= :businessDate
              and (t.effectiveTo is null or t.effectiveTo >= :businessDate)
            order by t.effectiveFrom desc, t.id desc
            """)
    List<TaxRateEntity> findEffectiveRates(
            @Param("productIds") Collection<Long> productIds,
            @Param("businessDate") LocalDate businessDate
    );
}
