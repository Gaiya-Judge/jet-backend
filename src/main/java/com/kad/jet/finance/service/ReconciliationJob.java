package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.WebhookEvent;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import com.kad.jet.notification.event.ReconciliationJobPaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaystackClient paystackClient;
    private final WebhookEventRepository webhookEventRepository;
    private final InternalPaymentHelperService paymentHelperService;
    private final ApplicationEventPublisher eventPublisher;
    private final WebhookEventProcessingService webhookEventProcessingService;
    private final WebhookEventPersistenceService webhookEventPersistenceService;

    @Scheduled(fixedRate = 600000)
    public void reconcilePendingPayment(){

        LocalDateTime threshold = LocalDateTime.now()
                .minusMinutes(5);

        List<Payment> pendingPayment = paymentRepository.findPendingPaystackPaymentsOlderThan(threshold);

        if (pendingPayment.isEmpty()){
            return;
        }

        log.info("Reconciliation job started. " +
                "found {} pending job to check ",
                pendingPayment.size());

        int verified = 0;
        int failed = 0;
        int stillPending = 0;

        for (Payment payment : pendingPayment){

            try {

                String reference = payment.getPaystackReference();

                Map<String, Object> paystackData =
                        paystackClient.verifyTransaction(reference);

                String status = (String) paystackData.get("status");

                if ("success".equals(status)) {
                    paymentHelperService.processVerifiedPayment(payment, paystackData);
                    verified++;

                } else if ("failed".equals(status)
                        || "abandoned".equals(status)) {

                    paymentHelperService.markPaymentFailed(payment);
                    failed++;
                    eventPublisher.publishEvent(
                            new ReconciliationJobPaymentFailedEvent(
                                    payment.getId(),
                                    payment.getTuitionAccount().getStudent().getId()
                            )
                    );
                } else {

                    stillPending++;

                    log.info("payment {} still processing " +
                                    "on paystack. status: {} ",
                            reference, status);
                }

            } catch (Exception e) {
                log.error("Could not reconcile payment {}. {}",
                        payment.getId(), e.getMessage());
            }
        }

        log.info("Reconciliation complete. "+
                "Verified: {}. Failed: {}. Still Pending {}.",
                verified, failed, stillPending);
    }

    @Scheduled(fixedRate = 180000)
    public void retryFailedWebhookEvent(){

        List<WebhookEvent> failedEvent = webhookEventRepository
                .findByProcessedFalse();

        if (failedEvent.isEmpty()) return;

        log.info("Found {} unproccessed webhook event to retry",
                failedEvent.size());

        for (WebhookEvent event : failedEvent){
            try {
                if (!"charge.success".equals(event.getEventType())) {
                    webhookEventProcessingService.processRetryEvent(
                            event.getId(), null);
                    continue;
                }

                log.info("Retrying webhook event for reference: {}",
                        event.getReference());

                Payment payment = paymentRepository
                        .findByIdempotencyKey(event.getReference())
                        .orElseThrow(() -> new IllegalStateException(
                                "payment not found for webhook reference: "
                                        + event.getReference()));
                Map<String, Object> paystackData = null;
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    paystackData = paystackClient
                            .verifyTransaction(event.getReference());
                }

                webhookEventProcessingService.processRetryEvent(
                        event.getId(), paystackData);
            } catch (Exception exception) {
                webhookEventPersistenceService.recordProcessingFailure(
                        event.getId(), exception.getMessage());
                log.error("Error retrying webhook event {}: {}",
                        event.getReference(),
                        exception.getMessage());
            }
        }
    }

}
