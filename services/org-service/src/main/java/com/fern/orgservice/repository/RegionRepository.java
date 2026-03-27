package com.fern.orgservice.repository;

import com.fern.orgservice.domain.RegionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<RegionEntity, Long> {
    Optional<RegionEntity> findByCode(String code);
}
