package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.RecipeVersionIngredientEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeVersionIngredientRepository extends JpaRepository<RecipeVersionIngredientEntity, Long> {
    @EntityGraph(attributePaths = {"ingredient"})
    List<RecipeVersionIngredientEntity> findByRecipeVersion_IdOrderBySortOrderAsc(Long recipeVersionId);

    @EntityGraph(attributePaths = {"ingredient", "recipeVersion"})
    List<RecipeVersionIngredientEntity> findByRecipeVersion_IdInOrderByRecipeVersion_IdAscSortOrderAsc(Collection<Long> recipeVersionIds);

    void deleteByRecipeVersion_Id(Long recipeVersionId);
}
