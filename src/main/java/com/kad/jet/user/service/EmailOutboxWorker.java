package com.kad.jet.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailOutboxWorker {

    private final EmailOutboxClaimService claimService;
    private final EmailOutboxDeliveryService deliveryService;

    @Value("${activation.outbox.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${activation.outbox.poll-interval-ms:5000}")
    public void processPendingMessages() {
        for (var messageId : claimService.claimBatch(batchSize)) {
            try {
                deliveryService.deliver(messageId);
            } catch (RuntimeException e) {
                // Log only the outbox id; never log message payloads, tokens, or URLs.
                log.error("Activation email outbox processing failed for message {}", messageId);
            }
        }
    }
}
