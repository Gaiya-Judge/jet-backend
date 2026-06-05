package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    // Find unprocessed events — used by future reconciliation job
    List<WebhookEvent> findByProcessedFalse();

    // Check if we already persisted an event for this reference
    boolean existsByReference(String reference);
}
