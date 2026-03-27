package com.fern.orgservice.repository;

import com.fern.orgservice.domain.OutletEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutletRepository extends JpaRepository<OutletEntity, Long> {
    Optional<OutletEntity> findByCode(String code);
}
