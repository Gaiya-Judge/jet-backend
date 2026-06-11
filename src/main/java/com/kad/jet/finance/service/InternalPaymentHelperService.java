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

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalPaymentHelperService {

    private final PaymentRepository paymentRepository;

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


}