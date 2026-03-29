package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.UnitOfMeasureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasureEntity, String> {
}
