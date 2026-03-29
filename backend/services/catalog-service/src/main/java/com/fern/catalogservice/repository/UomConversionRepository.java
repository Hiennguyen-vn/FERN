package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.UomConversionEntity;
import com.fern.catalogservice.domain.UomConversionId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UomConversionRepository extends JpaRepository<UomConversionEntity, UomConversionId> {
    List<UomConversionEntity> findAllByOrderByIdFromUomCodeAscIdToUomCodeAsc();
}
