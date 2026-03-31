package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.ProductEntity;
import com.fern.catalogservice.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(String code, Long id);

    List<ProductEntity> findAllByDeletedAtIsNullOrderByCodeAsc(org.springframework.data.domain.Pageable pageable);

    List<ProductEntity> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

    List<ProductEntity> findByIdInAndStatusAndDeletedAtIsNull(Collection<Long> ids, ProductStatus status);
}
