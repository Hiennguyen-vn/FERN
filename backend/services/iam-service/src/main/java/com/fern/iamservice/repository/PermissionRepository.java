package com.fern.iamservice.repository;

import com.fern.iamservice.domain.PermissionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
    Optional<PermissionEntity> findByCode(String code);

    List<PermissionEntity> findAllByCodeIn(Collection<String> codes);
}
