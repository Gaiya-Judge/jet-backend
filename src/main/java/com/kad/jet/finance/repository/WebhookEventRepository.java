package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.WebhookEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    // Find unprocessed events — used by future reconciliation job
    List<WebhookEvent> findByProcessedFalse();

    Optional<WebhookEvent> findByEventIdentity(String eventIdentity);

    List<WebhookEvent> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM WebhookEvent e WHERE e.id = :id")
    Optional<WebhookEvent> findByIdWithLock(@Param("id") UUID id);

    // Check if we already persisted an event for this reference
    boolean existsByReference(String reference);
}
