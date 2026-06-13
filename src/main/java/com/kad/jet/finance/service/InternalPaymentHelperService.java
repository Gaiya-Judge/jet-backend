package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalPaymentHelperService {

    private final PaymentRepository paymentRepository;
    private final TuitionAccountDomainService tuitionAccountDomainService;

    @Transactional
    public void saveInitiatedPayment(
            TuitionAccount tuitionAccount,
            BigDecimal amount,
            String reference) {

        Payment payment = Payment.builder()
                .tuitionAccount(tuitionAccount)
                .idempotencyKey(reference)
                .amount(amount)
                .paymentMethod(PaymentMethod.PAYSTACK)
                .status(PaymentStatus.PENDING)
                .paystackReference(reference)
                .initiatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();

        paymentRepository.save(payment);

        log.info("Payment record saved. Reference: {} Status: PENDING", reference);
    }

    @Transactional
    public void markPaymentFailed(Payment payment){
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("payment {} marked FAILED by reconciliation job ",
                payment.getPaystackReference());
    }

    @Transactional
    public void processVerifiedPayment(Payment payment, Map<String, Object> paystackData){

        //get amount
        Object amountObj = paystackData.get("amount");
        BigDecimal paidAmountNaira = BigDecimal
                .valueOf(((Number) amountObj).longValue())
                .divide(BigDecimal.valueOf(100));

        if (paidAmountNaira.compareTo(payment.getAmount()) != 0){
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

        tuitionAccountDomainService.updateTuitionAccount(payment);

        log.info("Payment {} verified via reconciliation. " +
                        "Amount: ₦{}",
                payment.getPaystackReference(),
                paidAmountNaira);

    }
}