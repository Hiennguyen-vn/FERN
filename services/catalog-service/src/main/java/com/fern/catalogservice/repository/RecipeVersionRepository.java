package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.RecipeVersionEntity;
import com.fern.catalogservice.domain.RecipeVersionStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersionEntity, Long> {
    @EntityGraph(attributePaths = {"recipe", "recipe.product"})
    List<RecipeVersionEntity> findByRecipe_IdOrderByEffectiveFromDesc(Long recipeId);

    @Query("""
            select (count(rv) > 0)
            from RecipeVersionEntity rv
            join rv.recipe recipe
            join recipe.product product
            where product.id = :productId
              and rv.status = :status
              and (:excludeId is null or rv.id <> :excludeId)
              and rv.effectiveFrom <= coalesce(:effectiveTo, rv.effectiveFrom)
              and coalesce(rv.effectiveTo, :effectiveTo) >= :effectiveFrom
            """)
    boolean existsOverlappingActiveVersion(
            @Param("productId") Long productId,
            @Param("status") RecipeVersionStatus status,
            @Param("excludeId") Long excludeId,
            @Param("effectiveFrom") LocalDate effectiveFrom,
            @Param("effectiveTo") LocalDate effectiveTo
    );

    @EntityGraph(attributePaths = {"recipe", "recipe.product"})
    @Query("""
            select rv
            from RecipeVersionEntity rv
            join rv.recipe recipe
            join recipe.product product
            where product.id in :productIds
              and rv.status = :status
              and rv.effectiveFrom <= :businessDate
              and (rv.effectiveTo is null or rv.effectiveTo >= :businessDate)
            order by rv.effectiveFrom desc, rv.id desc
            """)
    List<RecipeVersionEntity> findEffectiveVersions(
            @Param("productIds") Collection<Long> productIds,
            @Param("status") RecipeVersionStatus status,
            @Param("businessDate") LocalDate businessDate
    );
}
