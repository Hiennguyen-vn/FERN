package com.fern.iamservice.repository;

import com.fern.iamservice.domain.IamOutboxEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IamOutboxRepository extends JpaRepository<IamOutboxEventEntity, UUID> {
    List<IamOutboxEventEntity> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
