package com.kad.jet.user.service;

import com.kad.jet.user.model.EmailOutboxMessage;
import com.kad.jet.user.model.EmailOutboxStatus;
import com.kad.jet.user.repository.EmailOutboxRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailOutboxClaimService {

    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(10);
    private final EmailOutboxRepository repository;

    @Value("${activation.outbox.maximum-attempts:8}")
    private int maximumAttempts;

    @Transactional
    public List<UUID> claimBatch(int batchSize) {
        Instant now = Instant.now();
        List<EmailOutboxMessage> messages = repository.findClaimable(
                EmailOutboxStatus.PENDING,
                EmailOutboxStatus.PROCESSING,
                now,
                PageRequest.of(0, batchSize));

        java.util.List<UUID> claimedIds = new java.util.ArrayList<>();
        for (EmailOutboxMessage message : messages) {
            if (message.getStatus() == EmailOutboxStatus.PROCESSING
                    && message.getAttemptCount() >= maximumAttempts) {
                message.setStatus(EmailOutboxStatus.FAILED);
                message.setLeaseUntil(null);
                message.setLastErrorCode("PROCESSING_LEASE_EXPIRED_MAX_ATTEMPTS");
                continue;
            }
            message.setStatus(EmailOutboxStatus.PROCESSING);
            message.setAttemptCount(message.getAttemptCount() + 1);
            message.setLastAttemptAt(now);
            message.setLeaseUntil(now.plus(PROCESSING_LEASE));
            claimedIds.add(message.getId());
        }
        return claimedIds;
    }
}
