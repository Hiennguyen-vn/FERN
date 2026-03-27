package com.fern.iamservice.repository;

import com.fern.iamservice.domain.UserScopeAssignmentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface UserScopeAssignmentRepository extends JpaRepository<UserScopeAssignmentEntity, Long> {
    List<UserScopeAssignmentEntity> findAllByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
