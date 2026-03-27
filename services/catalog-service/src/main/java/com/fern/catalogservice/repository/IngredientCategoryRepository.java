package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.IngredientCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientCategoryRepository extends JpaRepository<IngredientCategoryEntity, String> {
}
