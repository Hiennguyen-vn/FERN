package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.ProductCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategoryEntity, String> {
}
