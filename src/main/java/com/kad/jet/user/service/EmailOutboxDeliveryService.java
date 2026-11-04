package com.kad.jet.user.service;

import com.kad.jet.user.model.EmailOutboxMessage;
import com.kad.jet.user.model.EmailOutboxStatus;
import com.kad.jet.user.repository.ActivationTokenRepository;
import com.kad.jet.user.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailOutboxDeliveryService {

    private final EmailOutboxRepository outboxRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final ActivationPayloadCrypto payloadCrypto;
    private final ActivationEmailSender emailSender;
    private final EmailOutboxStateService stateService;

    public void deliver(UUID messageId) {
        EmailOutboxMessage message = outboxRepository.findWithActivationTokenById(messageId).orElse(null);
        if (message == null || message.getStatus() != EmailOutboxStatus.PROCESSING) {
            return;
        }

        if (!activationTokenRepository.existsByIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                message.getActivationToken().getId(), Instant.now())) {
            stateService.markCancelled(messageId);
            return;
        }

        try {
            String rawToken = payloadCrypto.decrypt(
                    message.getEncryptedPayload(),
                    message.getActivationToken().getId() + ":" + message.getRecipient());
            emailSender.sendActivationEmail(message.getRecipient(), rawToken);
            stateService.markDelivered(messageId);
        } catch (EmailDeliveryException e) {
            stateService.markFailed(messageId, e.isPermanent(), e.getErrorCode());
        } catch (RuntimeException e) {
            stateService.markFailed(messageId, true, "OUTBOX_PAYLOAD_UNAVAILABLE");
        }
    }
}
