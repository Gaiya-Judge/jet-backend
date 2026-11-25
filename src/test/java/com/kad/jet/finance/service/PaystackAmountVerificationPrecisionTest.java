package com.kad.jet.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.config.PaystackConfig;
import com.kad.jet.config.service.SystemConfigService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaystackAmountVerificationPrecisionTest {

    private static final String REFERENCE = "precision-test-reference";
    private static final String WEBHOOK_SECRET = "precision-test-webhook-secret";

    @Mock private PaymentRepository paymentRepository;
    @Mock private TuitionAccountRepository tuitionAccountRepository;
    @Mock private RegistrationRepository registrationRepository;
    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private WebhookEventRepository webhookEventRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private PaystackClient paystackClient;
    @Mock private PaystackConfig paystackConfig;
    @Mock private ObjectMapper objectMapper;
    @Mock private InternalPaymentHelperService internalPaymentHelperService;
    @Mock private TuitionAccountDomainService tuitionAccountDomainService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private PaymentServiceImp paymentService;
    private InternalPaymentHelperService paymentHelperService;
    private Payment payment;
    private _User student;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImp(
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
        paymentHelperService = new InternalPaymentHelperService(
                paymentRepository,
                tuitionAccountDomainService,
                eventPublisher);

        student = _User.builder()
                .id(UUID.randomUUID())
                .fullName("Precision Test Student")
                .userRole(UserRole.STUDENT)
                .build();
        payment = Payment.builder()
                .id(UUID.randomUUID())
                .idempotencyKey(REFERENCE)
                .paystackReference(REFERENCE)
                .amount(new BigDecimal("70.00"))
                .paymentMethod(PaymentMethod.PAYSTACK)
                .status(PaymentStatus.PENDING)
                .appliedToAccount(false)
                .tuitionAccount(TuitionAccount.builder()
                        .id(UUID.randomUUID())
                        .student(student)
                        .registration(Registration.builder()
                                .id(UUID.randomUUID())
                                .build())
                        .build())
                .build();
    }

    @Test
    void manualVerificationDoesNotApplyFractionalKoboAmount() {
        when(paymentRepository.findByIdempotencyKey(REFERENCE))
                .thenReturn(Optional.of(payment));
        when(paystackClient.verifyTransaction(REFERENCE))
                .thenReturn(Map.of(
                        "status", "success",
                        "amount", new BigDecimal("7000.5")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.verifyPaymentStatus(student, REFERENCE));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(tuitionAccountDomainService, never())
                .updateTuitionAccountAndProcessRegistration(payment.getId());
    }

    @Test
    void webhookDoesNotApplyFractionalKoboAmount() throws Exception {
        when(paymentRepository.findByIdempotencyKey(REFERENCE))
                .thenReturn(Optional.of(payment));
        when(paystackConfig.getSecretKey()).thenReturn(WEBHOOK_SECRET);
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenReturn(Map.of(
                        "event", "charge.success",
                        "data", Map.of(
                                "reference", REFERENCE,
                                "status", "success",
                                "amount", new BigDecimal("7000.5"))));

        String payload =
                "{\"event\":\"charge.success\",\"data\":{\"reference\":\""
                        + REFERENCE + "\"}}";
        paymentService.handleWebhook(signature(payload), payload);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(payment);
        verify(tuitionAccountDomainService, never())
                .updateTuitionAccountAndProcessRegistration(payment.getId());
        verify(eventPublisher, never())
                .publishEvent(any(PaymentVerifiedEvent.class));
    }

    @Test
    void reconciliationDoesNotApplyFractionalKoboAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> paymentHelperService.processVerifiedPayment(
                        payment,
                        Map.of("amount", new BigDecimal("7000.5"))));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(paymentRepository, never()).save(payment);
        verify(tuitionAccountDomainService, never())
                .updateTuitionAccountAndProcessRegistration(payment.getId());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA512"));
        return HexFormat.of().formatHex(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
