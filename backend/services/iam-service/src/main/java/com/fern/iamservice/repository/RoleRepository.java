package com.fern.iamservice.repository;

import com.fern.iamservice.domain.RoleEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByCode(String code);

    List<RoleEntity> findAllByCodeIn(Collection<String> codes);
}
