package com.fern.iamservice.repository;

import com.fern.iamservice.domain.RolePermissionEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {
    List<RolePermissionEntity> findAllByRoleIdIn(Collection<Long> roleIds);

    List<RolePermissionEntity> findAllByRoleId(Long roleId);

    @Transactional
    void deleteByRoleId(Long roleId);
}
