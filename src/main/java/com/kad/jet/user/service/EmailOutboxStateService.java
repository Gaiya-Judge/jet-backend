package com.kad.jet.user.service;

import com.kad.jet.user.model.EmailOutboxStatus;
import com.kad.jet.user.repository.EmailOutboxRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailOutboxStateService {

    private final EmailOutboxRepository repository;

    @Value("${activation.outbox.maximum-attempts:8}")
    private int maximumAttempts;

    @Value("${activation.outbox.initial-retry-delay:PT30S}")
    private Duration initialRetryDelay;

    @Value("${activation.outbox.maximum-retry-delay:PT6H}")
    private Duration maximumRetryDelay;

    @Transactional
    public void markDelivered(UUID messageId) {
        repository.findById(messageId).ifPresent(message -> {
            message.setStatus(EmailOutboxStatus.SENT);
            message.setDeliveredAt(Instant.now());
            message.setLeaseUntil(null);
            message.setLastErrorCode(null);
        });
    }

    @Transactional
    public void markCancelled(UUID messageId) {
        repository.findById(messageId).ifPresent(message -> {
            message.setStatus(EmailOutboxStatus.CANCELLED);
            message.setLeaseUntil(null);
        });
    }

    @Transactional
    public void markFailed(UUID messageId, boolean permanent, String errorCode) {
        repository.findById(messageId).ifPresent(message -> {
            message.setLastErrorCode(errorCode);
            message.setLeaseUntil(null);
            if (permanent || message.getAttemptCount() >= maximumAttempts) {
                message.setStatus(EmailOutboxStatus.FAILED);
                return;
            }
            message.setStatus(EmailOutboxStatus.PENDING);
            message.setNextAttemptAt(Instant.now().plus(retryDelay(message.getAttemptCount())));
        });
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 20);
        Duration delay = initialRetryDelay.multipliedBy(multiplier);
        return delay.compareTo(maximumRetryDelay) > 0 ? maximumRetryDelay : delay;
    }
}
