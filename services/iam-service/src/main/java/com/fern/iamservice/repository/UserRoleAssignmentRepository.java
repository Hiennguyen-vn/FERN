package com.fern.iamservice.repository;

import com.fern.iamservice.domain.UserRoleAssignmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, Long> {
    List<UserRoleAssignmentEntity> findAllByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
