package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.RecipeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<RecipeEntity, Long> {
    Optional<RecipeEntity> findByProduct_Id(Long productId);

    List<RecipeEntity> findAllByProduct_IdIn(Collection<Long> productIds);

    boolean existsByRecipeCodeIgnoreCase(String recipeCode);

    boolean existsByRecipeCodeIgnoreCaseAndIdNot(String recipeCode, Long id);

    List<RecipeEntity> findAllByOrderByRecipeCodeAsc();
}
