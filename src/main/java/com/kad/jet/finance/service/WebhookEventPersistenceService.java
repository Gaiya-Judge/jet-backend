package com.kad.jet.finance.service;

import com.kad.jet.finance.model.WebhookEvent;
import com.kad.jet.finance.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookEventPersistenceService {

    private final WebhookEventRepository webhookEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID persist(WebhookEvent webhookEvent) {
        return webhookEventRepository.saveAndFlush(webhookEvent).getId();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findIdByEventIdentity(String eventIdentity) {
        return webhookEventRepository.findByEventIdentity(eventIdentity)
                .map(WebhookEvent::getId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProcessingFailure(UUID eventId, String errorMessage) {
        WebhookEvent webhookEvent = webhookEventRepository
                .findByIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "webhook event not found: " + eventId));

        if (Boolean.TRUE.equals(webhookEvent.getProcessed())) {
            return;
        }

        webhookEvent.setProcessed(false);
        webhookEvent.setProcessedAt(null);
        webhookEvent.setProcessingError(errorMessage);
        webhookEventRepository.save(webhookEvent);
    }

}
