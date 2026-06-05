package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.WebhookEvent;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import jakarta.transaction.Transactional;
import jdk.jfr.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaystackClient paystackClient;
    private final PaymentServiceImp paymentService;
    private final WebhookEventRepository webhookEventRepository;

    @Scheduled(fixedRate = 6000000)
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

                Map<String, Object> paystackData = paystackClient.verifyTransaction(reference);

                String status = (String) paystackData.get("status");

                if ("success".equals(status)) {
                    processVerifiedPayment(payment, paystackData);
                    verified++;

                } else if ("failed".equals(status)
                        || "abandoned".equals(status)) {

                    markPaymentFailed(payment);
                    failed++;
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
                // Only retry charge.success events
                if (!"charge.success".equals(event.getEventType())) {
                    event.setProcessed(true);
                    event.setProcessedAt(LocalDateTime.now());
                    webhookEventRepository.save(event);
                    continue;
                }

                log.info("Retrying webhook event for reference: {}",
                        event.getReference());


                //find and process payment
                paymentRepository
                        .findByIdempotencyKey(event.getReference())
                        .ifPresent(payment -> {
                            if (payment.getStatus().equals(PaymentStatus.VERIFIED)){
                                //re-verify with paystack before process

                                try {
                                    Map<String, Object> data =
                                            paystackClient
                                                    .verifyTransaction(event.getReference());

                                    if ("success".equals(
                                            data.get("status"))){
                                                processVerifiedPayment(payment, data);
                                                event.setProcessed(true);
                                                event.setProcessedAt(LocalDateTime.now());
                                                webhookEventRepository
                                                        .save(event);
                                    }

                                } catch (Exception e) {
                                    log.error("Retry failed for " +
                                            " reference {}: {}.",
                                            event.getReference(),
                                            e.getMessage());
                                }
                            } else {
                                event.setProcessed(true);
                                event.setProcessedAt(LocalDateTime.now());
                                webhookEventRepository
                                        .save(event);
                            }
                        });
            } catch (Exception e) {
                log.error("Error retying webhook event {}: {}",
                        event.getReference(),
                        e.getMessage());
            }
        }
    }

    @Transactional
    private void processVerifiedPayment(Payment payment, Map<String, Object> paystackData){

        //get amount
        Object amountObj = paystackData.get("amount");
        BigDecimal paidAmountNaira = BigDecimal
                .valueOf(((Number) amountObj).longValue())
                .divide(BigDecimal.valueOf(100));

        if (paidAmountNaira.compareTo(BigDecimal.ZERO) != 0){

            log.error("Amount mismatch for reconciliation " +
                    "for reference {}. expected: {}. got {} ",
                    payment.getPaystackReference(),
                    payment.getAmount(),
                    paidAmountNaira);
            markPaymentFailed(payment);
            return;
        }

        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setVerifiedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        paymentService.updateTuitionAccount(payment);

        log.info("Payment {} verified via reconciliation. " +
                        "Amount: ₦{}",
                payment.getPaystackReference(),
                paidAmountNaira);

    }

    @Transactional
    private void markPaymentFailed(Payment payment){
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("payment {} marked FAILED by reconciliation job ",
                payment.getPaystackReference());
    }
}
