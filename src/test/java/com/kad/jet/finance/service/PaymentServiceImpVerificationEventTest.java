package com.kad.jet.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.config.PaystackConfig;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.finance.dto.PaymentResponse;
import com.kad.jet.finance.dto.VerifyPaymentRequest;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import com.kad.jet.notification.event.PaymentVerifiedEvent;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImpVerificationEventTest {

    private static final String REFERENCE = "payment-reference";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";
    private static final BigDecimal AMOUNT = new BigDecimal("70.00");

    @Mock PaymentRepository paymentRepository;
    @Mock TuitionAccountRepository tuitionAccountRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock StudentProfileRepository studentProfileRepository;
    @Mock WebhookEventRepository webhookEventRepository;
    @Mock SystemConfigService systemConfigService;
    @Mock PaystackClient paystackClient;
    @Mock PaystackConfig paystackConfig;
    @Mock ObjectMapper objectMapper;
    @Mock InternalPaymentHelperService internalPaymentHelperService;
    @Mock TuitionAccountDomainService tuitionAccountDomainService;
    @Mock ApplicationEventPublisher eventPublisher;

    private PaymentServiceImp service;
    private Payment payment;
    private _User student;
    private _User bursar;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImp(
                paymentRepository,
                tuitionAccountRepository,
                registrationRepository,
                studentProfileRepository,
                webhookEventRepository,
                systemConfigService,
                paystackClient,
                paystackConfig,
                objectMapper,
                internalPaymentHelperService,
                tuitionAccountDomainService,
                eventPublisher);

        student = _User.builder()
                .id(UUID.randomUUID())
                .fullName("Student")
                .userRole(UserRole.STUDENT)
                .build();
        bursar = _User.builder()
                .id(UUID.randomUUID())
                .fullName("Bursar")
                .userRole(UserRole.BURSAR)
                .build();
        payment = Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(REFERENCE)
                .paystackReference(REFERENCE)
                .amount(AMOUNT)
                .paymentMethod(PaymentMethod.PAYSTACK)
                .status(PaymentStatus.PENDING)
                .appliedToAccount(false)
                .tuitionAccount(TuitionAccount.builder()
                        .id(UUID.randomUUID())
                        .student(student)
                        .registration(Registration.builder().id(UUID.randomUUID()).build())
                        .build())
                .build();
    }

    @Test
    void webhookEventUsesOutcomeSetByAccountApplication() throws Exception {
        payment.setPaymentMethod(PaymentMethod.PAYSTACK);
        when(paymentRepository.findByIdempotencyKey(REFERENCE)).thenReturn(Optional.of(payment));
        when(paystackConfig.getSecretKey()).thenReturn(WEBHOOK_SECRET);
        when(objectMapper.readValue(any(String.class), any(Class.class)))
                .thenReturn(Map.of(
                        "event", "charge.success",
                        "data", Map.of("reference", REFERENCE, "status", "success", "amount", 7000)));
        setApplicationOutcome(true);

        String payload = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"payment-reference\"}}";
        service.handleWebhook(signature(payload), payload);

        assertPublishedOutcome(true);
    }

    @Test
    void bursarVerificationReportsVerifiedPaymentAsUnappliedForPaidAccount() {
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        setApplicationOutcome(false);

        PaymentResponse response = service.verifyBankTransfer(
                bursar, payment.getId(), new VerifyPaymentRequest());

        assertFalse(response.getAppliedToAccount());
        assertPublishedOutcome(false);
    }

    @Test
    void studentPaystackStatusEventUsesOutcomeSetByAccountApplication() {
        when(paymentRepository.findByIdempotencyKey(REFERENCE)).thenReturn(Optional.of(payment));
        when(paystackClient.verifyTransaction(REFERENCE))
                .thenReturn(Map.of("status", "success", "amount", 7000));
        setApplicationOutcome(true);

        service.verifyPaymentStatus(student, REFERENCE);

        assertPublishedOutcome(true);
    }

    @Test
    void duplicateAppliedWebhookDoesNotPublishAnotherVerifiedEvent() throws Exception {
        payment.setStatus(PaymentStatus.VERIFIED);
        payment.setAppliedToAccount(true);
        when(paymentRepository.findByIdempotencyKey(REFERENCE)).thenReturn(Optional.of(payment));
        when(paystackConfig.getSecretKey()).thenReturn(WEBHOOK_SECRET);
        when(objectMapper.readValue(any(String.class), any(Class.class)))
                .thenReturn(Map.of(
                        "event", "charge.success",
                        "data", Map.of("reference", REFERENCE, "status", "success", "amount", 7000)));

        String payload = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"payment-reference\"}}";
        service.handleWebhook(signature(payload), payload);

        verify(eventPublisher, never()).publishEvent(any(PaymentVerifiedEvent.class));
        verify(webhookEventRepository, never()).save(any());
        verify(tuitionAccountDomainService, never())
                .updateTuitionAccountAndProcessRegistration(payment.getId());
    }

    private void setApplicationOutcome(boolean applied) {
        when(tuitionAccountDomainService.updateTuitionAccountAndProcessRegistration(payment.getId()))
                .thenAnswer(invocation -> {
                    payment.setAppliedToAccount(applied);
                    return applied;
                });
    }

    private void assertPublishedOutcome(boolean expectedApplied) {
        ArgumentCaptor<PaymentVerifiedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentVerifiedEvent.class);
        var callOrder = inOrder(tuitionAccountDomainService, eventPublisher);
        callOrder.verify(tuitionAccountDomainService)
                .updateTuitionAccountAndProcessRegistration(payment.getId());
        callOrder.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(expectedApplied, eventCaptor.getValue().appliedToAccount());
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}