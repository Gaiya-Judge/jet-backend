package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import jakarta.transaction.Transactional;
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

            String reference = payment.getPaystackReference();

            Map<String, Object> paystackData = paystackClient.verifyTransaction(reference);
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
