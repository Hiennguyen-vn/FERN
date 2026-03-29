package com.fern.iamservice.repository;

import com.fern.iamservice.domain.UserPermissionOverrideEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionOverrideRepository extends JpaRepository<UserPermissionOverrideEntity, Long> {
    List<UserPermissionOverrideEntity> findAllByUserId(Long userId);

    void deleteByUserId(Long userId);
}
