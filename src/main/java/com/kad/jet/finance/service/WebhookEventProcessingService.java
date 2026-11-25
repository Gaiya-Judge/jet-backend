package com.kad.jet.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.WebhookEvent;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import com.kad.jet.notification.event.PaymentVerifiedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookEventProcessingService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final InternalPaymentHelperService paymentHelperService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processIncomingEvent(UUID eventId) {
        WebhookEvent event = lockEvent(eventId);
        if (Boolean.TRUE.equals(event.getProcessed())) {
            return;
        }

        Map<String, Object> payload = readPayload(event.getRawPayload());
        if (!"charge.success".equals(event.getEventType())) {
            markProcessed(event);
            return;
        }

        Map<String, Object> data = readData(payload);
        Payment payment = findPayment(event);

        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            if (!Boolean.TRUE.equals(payment.getAppliedToAccount())) {
                paymentHelperService.applyVerifiedPayment(payment);
            }
            markProcessed(event);
            return;
        }

        Object statusObject = data.get("status");
        if (!(statusObject instanceof String status)) {
            throw new IllegalArgumentException(
                    "charge.success event has no valid transaction status");
        }
        if (!"success".equals(status)) {
            paymentHelperService.markPaymentFailed(payment);
            markProcessed(event);
            return;
        }

        InternalPaymentHelperService.VerifiedPaymentOutcome outcome =
                paymentHelperService.processWebhookPayment(payment, data);
        if (outcome.verified()) {
            eventPublisher.publishEvent(new PaymentVerifiedEvent(
                    payment.getId(),
                    payment.getTuitionAccount().getId(),
                    payment.getTuitionAccount().getStudent().getId(),
                    payment.getAmount(),
                    outcome.appliedToAccount()));
        }
        markProcessed(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRetryEvent(
            UUID eventId,
            Map<String, Object> paystackData) {
        WebhookEvent event = lockEvent(eventId);
        if (Boolean.TRUE.equals(event.getProcessed())) {
            return;
        }
        if (!"charge.success".equals(event.getEventType())) {
            markProcessed(event);
            return;
        }

        Payment payment = findPayment(event);
        if (payment.getStatus() == PaymentStatus.VERIFIED) {
            if (!Boolean.TRUE.equals(payment.getAppliedToAccount())) {
                paymentHelperService.applyVerifiedPayment(payment);
            }
            markProcessed(event);
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            markProcessed(event);
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "payment cannot be retried from status "
                            + payment.getStatus());
        }
        if (paystackData == null) {
            throw new IllegalStateException(
                    "Paystack verification data is required for retry");
        }

        Object statusObject = paystackData.get("status");
        if (!(statusObject instanceof String status)) {
            throw new IllegalArgumentException(
                    "Paystack verification returned no valid status");
        }
        if ("success".equals(status)) {
            paymentHelperService.processVerifiedPayment(payment, paystackData);
            markProcessed(event);
        } else if ("failed".equals(status) || "abandoned".equals(status)) {
            paymentHelperService.markPaymentFailed(payment);
            markProcessed(event);
        } else {
            throw new IllegalStateException(
                    "Paystack payment remains unconfirmed: " + status);
        }
    }

    private WebhookEvent lockEvent(UUID eventId) {
        return webhookEventRepository.findByIdWithLock(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "webhook event not found: " + eventId));
    }

    private Payment findPayment(WebhookEvent event) {
        return paymentRepository.findByIdempotencyKey(event.getReference())
                .orElseThrow(() -> new IllegalStateException(
                        "payment not found for webhook reference: "
                                + event.getReference()));
    }

    private Map<String, Object> readPayload(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, Map.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "persisted webhook payload is invalid", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readData(Map<String, Object> payload) {
        Object data = payload.get("data");
        if (!(data instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "charge.success event has no valid data object");
        }
        return (Map<String, Object>) data;
    }

    private void markProcessed(WebhookEvent event) {
        event.setProcessed(true);
        event.setProcessedAt(LocalDateTime.now());
        event.setProcessingError(null);
        webhookEventRepository.save(event);
    }
}
