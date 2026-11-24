package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.notification.event.ReconciliationJobPaymentVerifiedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
        private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void markPaymentFailed(Payment payment){
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        log.info("payment {} marked FAILED by reconciliation job ",
                payment.getPaystackReference());
    }

    @Transactional
    public void applyVerifiedPayment(Payment payment) {
        boolean appliedToAccount = tuitionAccountDomainService
                .updateTuitionAccountAndProcessRegistration(payment.getId());

        if (appliedToAccount) {
            log.info("Verified payment {} applied to tuition account.",
                    payment.getPaystackReference());
        } else {
            log.warn("Verified payment {} was not applied because the tuition account is already PAID.",
                    payment.getPaystackReference());
        }
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

        boolean appliedToAccount = tuitionAccountDomainService
                .updateTuitionAccountAndProcessRegistration(payment.getId());

        eventPublisher.publishEvent(new ReconciliationJobPaymentVerifiedEvent(
                payment.getId(),
                payment.getTuitionAccount().getId(),
                payment.getTuitionAccount().getStudent().getId(),
                payment.getAmount(),
                appliedToAccount
        ));

        log.info("Payment {} verified via reconciliation. " +
                        "Amount: ₦{}",
                payment.getPaystackReference(),
                paidAmountNaira);

    }
}