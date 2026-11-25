package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import com.kad.jet.notification.event.ReconciliationJobPaymentFailedEvent;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.user.model._User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationJobRecoveryTest {

    private static final String REFERENCE = "uncertain-payment-reference";

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaystackClient paystackClient;
    @Mock private PaymentServiceImp paymentService;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private TuitionAccountDomainService tuitionAccountDomainService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private InternalPaymentHelperService paymentHelperService;
    private ReconciliationJob reconciliationJob;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentHelperService = new InternalPaymentHelperService(
                paymentRepository,
                tuitionAccountDomainService,
                eventPublisher);
        reconciliationJob = new ReconciliationJob(
                paymentRepository,
                paystackClient,
                paymentService,
                webhookEventRepository,
                paymentHelperService,
                eventPublisher);

        _User student = _User.builder()
                .id(UUID.randomUUID())
                .build();
        payment = Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(REFERENCE)
                .paystackReference(REFERENCE)
                .amount(new BigDecimal("70.00"))
                .paymentMethod(PaymentMethod.PAYSTACK)
                .status(PaymentStatus.PENDING)
                .appliedToAccount(false)
                .initiatedAt(LocalDateTime.now().minusMinutes(6))
                .tuitionAccount(TuitionAccount.builder()
                        .id(UUID.randomUUID())
                        .student(student)
                        .registration(Registration.builder()
                                .id(UUID.randomUUID())
                                .build())
                        .build())
                .build();
        when(paymentRepository.findPendingPaystackPaymentsOlderThan(
                any(LocalDateTime.class)))
                .thenReturn(List.of(payment));
    }

    @Test
    void pendingPaymentCanBeVerifiedOnReconciliationRetryAfterTimeout() {
        when(paystackClient.verifyTransaction(REFERENCE))
                .thenThrow(new ResourceAccessException(
                        "read timed out",
                        new SocketTimeoutException("read timed out")))
                .thenReturn(Map.of("status", "success", "amount", 7000));
        when(tuitionAccountDomainService
                .updateTuitionAccountAndProcessRegistration(payment.getId()))
                .thenReturn(true);

        reconciliationJob.reconcilePendingPayment();
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        reconciliationJob.reconcilePendingPayment();

        assertEquals(PaymentStatus.VERIFIED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(tuitionAccountDomainService)
                .updateTuitionAccountAndProcessRegistration(payment.getId());
    }

    @Test
    void reconciliationMarksOnlyProviderConfirmedFailedStatusAsFailed() {
        when(paystackClient.verifyTransaction(REFERENCE))
                .thenReturn(Map.of("status", "failed"));

        reconciliationJob.reconcilePendingPayment();

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(eventPublisher)
                .publishEvent(any(ReconciliationJobPaymentFailedEvent.class));
        verify(tuitionAccountDomainService, never())
                .updateTuitionAccountAndProcessRegistration(payment.getId());
    }
}
