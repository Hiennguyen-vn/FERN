package com.fern.orgservice.repository;

import com.fern.orgservice.domain.OutboxEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
