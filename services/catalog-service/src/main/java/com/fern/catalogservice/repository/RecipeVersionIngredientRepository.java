package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.RecipeVersionIngredientEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeVersionIngredientRepository extends JpaRepository<RecipeVersionIngredientEntity, Long> {
    List<RecipeVersionIngredientEntity> findByRecipeVersion_IdOrderBySortOrderAsc(Long recipeVersionId);

    void deleteByRecipeVersion_Id(Long recipeVersionId);
}
