package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.PromotionEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends JpaRepository<PromotionEntity, Long> {
    List<PromotionEntity> findAllByOrderByEffectiveFromDescIdDesc();

    List<PromotionEntity> findAllByCodeIgnoreCaseOrderByEffectiveFromDescIdDesc(String code);

    Optional<PromotionEntity> findByCodeAndStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
            String code,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    );

    @Query("""
            select p
            from PromotionEntity p
            where lower(p.code) = lower(:code)
              and p.status = :status
              and p.effectiveFrom <= :businessDate
              and p.effectiveTo is null
            order by p.effectiveFrom desc, p.id desc
            """)
    List<PromotionEntity> findOpenEndedEffectivePromotions(
            @Param("code") String code,
            @Param("status") String status,
            @Param("businessDate") LocalDate businessDate
    );

    @Query("""
            select p
            from PromotionEntity p
            where lower(p.code) = lower(:code)
              and p.status = :status
              and p.effectiveFrom <= :businessDate
              and (p.effectiveTo is null or p.effectiveTo >= :businessDate)
            order by p.effectiveFrom desc, p.id desc
            """)
    List<PromotionEntity> findApplicableCandidates(
            @Param("code") String code,
            @Param("status") String status,
            @Param("businessDate") LocalDate businessDate
    );

    @Query("""
            select (count(p) > 0)
            from PromotionEntity p
            where lower(p.code) = lower(:code)
              and upper(p.status) = upper(:status)
              and upper(p.scopeType) = upper(:scopeType)
              and ((:scopeId is null and p.scopeId is null) or p.scopeId = :scopeId)
              and (:excludeId is null or p.id <> :excludeId)
              and p.effectiveFrom <= coalesce(:effectiveTo, p.effectiveFrom)
              and (p.effectiveTo is null or p.effectiveTo >= :effectiveFrom)
            """)
    boolean existsActiveOverlap(
            @Param("code") String code,
            @Param("status") String status,
            @Param("scopeType") String scopeType,
            @Param("scopeId") Long scopeId,
            @Param("excludeId") Long excludeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );
}
