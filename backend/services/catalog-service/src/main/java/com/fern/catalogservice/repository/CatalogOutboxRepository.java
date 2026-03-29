package com.fern.catalogservice.repository;

import com.fern.catalogservice.domain.CatalogOutboxEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogOutboxRepository extends JpaRepository<CatalogOutboxEventEntity, UUID> {
    List<CatalogOutboxEventEntity> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
