package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.IngredientEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<IngredientEntity, Long> {
    Optional<IngredientEntity> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<IngredientEntity> findAllByDeletedAtIsNullOrderByCodeAsc();

    List<IngredientEntity> findByIdIn(Collection<Long> ids);
}
