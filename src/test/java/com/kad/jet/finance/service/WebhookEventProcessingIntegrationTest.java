package com.kad.jet.finance.service;

import com.kad.jet.config.PaystackConfig;
import com.kad.jet.finance.dto.InitiatePaymentRequest;
import com.kad.jet.finance.dto.InitiatePaymentResponse;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.model.WebhookEvent;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.finance.repository.WebhookEventRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
class WebhookEventProcessingIntegrationTest {

    private static final String WEBHOOK_SECRET = "task20-integration-secret";

    @Autowired private PaymentService paymentService;
    @Autowired private ReconciliationJob reconciliationJob;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TuitionAccountRepository tuitionAccountRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WebhookEventRepository webhookEventRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private PaystackClient paystackClient;
    @MockitoBean private PaystackConfig paystackConfig;
    @MockitoSpyBean private WebhookEventProcessingService webhookEventProcessingService;
    @MockitoSpyBean private TuitionAccountDomainService tuitionAccountDomainService;

    private final List<AccountFixture> fixtures = new ArrayList<>();
    private final List<String> webhookReferences = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(paystackConfig.getSecretKey()).thenReturn(WEBHOOK_SECRET);
    }

    @AfterEach
    void cleanUpFixtures() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            for (String reference : webhookReferences) {
                webhookEventRepository.deleteAll(
                        webhookEventRepository.findByReference(reference));
            }
            for (AccountFixture fixture : fixtures) {
                List<Payment> payments = paymentRepository
                        .findByTuitionAccountId(fixture.accountId());
                for (Payment payment : payments) {
                    if (payment.getPaystackReference() != null) {
                        webhookEventRepository.deleteAll(
                                webhookEventRepository.findByReference(
                                        payment.getPaystackReference()));
                    }
                }
                paymentRepository.deleteAll(payments);
                tuitionAccountRepository.deleteById(fixture.accountId());
                registrationRepository.deleteById(fixture.registrationId());
                semesterRepository.deleteById(fixture.semesterId());
                userRepository.deleteById(fixture.studentId());
            }
        });
    }

    @Test
    void successfulWebhookAndDuplicateDeliveryApplyTuitionCreditOnce() {
        Payment payment = createPendingPayment();
        String payload = chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                71001, "success", "7000");

        deliver(payload);
        deliver(payload);

        Payment refreshedPayment = paymentRepository
                .findById(payment.getId()).orElseThrow();
        TuitionAccount account = tuitionAccountRepository
                .findById(payment.getTuitionAccount().getId()).orElseThrow();
        List<WebhookEvent> events = webhookEventRepository
                .findByReference(payment.getIdempotencyKey());
        assertEquals(1, events.size());
        assertTrue(events.get(0).getProcessed());
        assertNull(events.get(0).getProcessingError());
        assertEquals(PaymentStatus.VERIFIED, refreshedPayment.getStatus());
        assertEquals(true, refreshedPayment.getAppliedToAccount());
        assertEquals(new BigDecimal("70.00"), account.getTotalPaid());

        deliver(chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                71002, "success", "7000"));

        assertEquals(2, webhookEventRepository
                .findByReference(payment.getIdempotencyKey()).size());
        assertEquals(new BigDecimal("70.00"),
                tuitionAccountRepository.findById(
                                payment.getTuitionAccount().getId())
                        .orElseThrow().getTotalPaid());
    }

    @Test
    void samePaymentReferenceWithDifferentEventTypesCreatesDistinctEvents() {
        Payment payment = createPendingPayment();

        deliver(chargePayload(
                "refund.processed", payment.getIdempotencyKey(),
                72001, "success", "7000"));
        deliver(chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                72001, "success", "7000"));

        List<WebhookEvent> events = webhookEventRepository
                .findByReference(payment.getIdempotencyKey());
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(WebhookEvent::getProcessed));
        assertFalse(events.get(0).getEventIdentity()
                .equals(events.get(1).getEventIdentity()));
        assertEquals(new BigDecimal("70.00"),
                tuitionAccountRepository.findById(
                                payment.getTuitionAccount().getId())
                        .orElseThrow().getTotalPaid());
    }

    @Test
    void canonicalPayloadHashDeduplicatesEquivalentJsonAndPreservesRawPayload() {
        String reference = "payload-fallback-" + UUID.randomUUID();
        String firstPayload = "{\"event\":\"refund.processed\",\"data\":{"
                + "\"reference\":\"" + reference + "\","
                + "\"status\":\"success\","
                + "\"metadata\":{\"outer\":{\"z\":2,"
                + "\"a\":[1,true,null]}}}}";
        String secondPayload = "{ \"data\" : { \"metadata\" : { "
                + "\"outer\" : { \"a\" : [1, true, null], \"z\" : 2 } }, "
                + "\"status\" : \"success\", \"reference\" : \"" + reference
                + "\" }, \"event\" : \"refund.processed\" }";

        webhookReferences.add(reference);
        paymentService.handleWebhook(signature(firstPayload), firstPayload);
        paymentService.handleWebhook(signature(secondPayload), secondPayload);

        List<WebhookEvent> events = webhookEventRepository
                .findByReference(reference);
        assertEquals(1, events.size());
        assertTrue(events.get(0).getProcessed());
        assertEquals(firstPayload, events.get(0).getRawPayload());
    }

    @Test
    void downstreamFailureLeavesPersistedEventRecoverableAndRetryCanApplyPayment() {
        Payment payment = createPendingPayment();
        deliver(chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                73001, "success", "7000.5"));

        WebhookEvent event = webhookEventRepository
                .findByReference(payment.getIdempotencyKey()).get(0);
        assertFalse(event.getProcessed());
        assertNotNull(event.getProcessingError());
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findById(payment.getId()).orElseThrow()
                        .getStatus());

        when(paystackClient.verifyTransaction(payment.getIdempotencyKey()))
                .thenReturn(Map.of("status", "success", "amount", 7000));
        reconciliationJob.retryFailedWebhookEvent();

        event = webhookEventRepository.findById(event.getId()).orElseThrow();
        assertTrue(event.getProcessed());
        assertNull(event.getProcessingError());
        assertEquals(PaymentStatus.VERIFIED,
                paymentRepository.findById(payment.getId()).orElseThrow()
                        .getStatus());
        assertEquals(new BigDecimal("70.00"),
                tuitionAccountRepository.findById(
                                payment.getTuitionAccount().getId())
                        .orElseThrow().getTotalPaid());
    }

    @Test
    void providerVerificationFailureLeavesEventUnprocessedWithError() {
        Payment payment = createPendingPayment();
        deliver(chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                74001, "success", "7000.5"));
        when(paystackClient.verifyTransaction(payment.getIdempotencyKey()))
                .thenThrow(new ResourceAccessException(
                        "read timed out",
                        new SocketTimeoutException("read timed out")));

        reconciliationJob.retryFailedWebhookEvent();

        WebhookEvent event = webhookEventRepository
                .findByReference(payment.getIdempotencyKey()).get(0);
        assertFalse(event.getProcessed());
        assertTrue(event.getProcessingError().contains("read timed out"));
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findById(payment.getId()).orElseThrow()
                        .getStatus());
    }

    @Test
    void missingPaymentDoesNotMarkWebhookSuccessfullyProcessed() {
        String reference = "missing-payment-" + UUID.randomUUID();
        deliver(chargePayload(
                "charge.success", reference, 75001, "success", "7000"));

        WebhookEvent event = webhookEventRepository
                .findByReference(reference).get(0);
        assertFalse(event.getProcessed());
        assertTrue(event.getProcessingError().contains("payment not found"));

        reconciliationJob.retryFailedWebhookEvent();

        event = webhookEventRepository.findById(event.getId()).orElseThrow();
        assertFalse(event.getProcessed());
        assertTrue(event.getProcessingError().contains("payment not found"));
    }

    @Test
    void unsupportedEventIsPersistedAndMarkedProcessed() {
        String reference = "unsupported-event-" + UUID.randomUUID();
        deliver(chargePayload(
                "refund.processed", reference, 76001, "success", "7000"));

        WebhookEvent event = webhookEventRepository
                .findByReference(reference).get(0);
        assertTrue(event.getProcessed());
        assertNull(event.getProcessingError());
        assertEquals("refund.processed", event.getEventType());
    }

    @Test
    void failedTuitionApplicationRollsBackAndKeepsEventRetryable() {
        Payment payment = createPendingPayment();
        doThrow(new IllegalStateException("account application failed"))
                .when(tuitionAccountDomainService)
                .updateTuitionAccountAndProcessRegistration(payment.getId());

        deliver(chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                76501, "success", "7000"));

        WebhookEvent event = webhookEventRepository
                .findByReference(payment.getIdempotencyKey()).get(0);
        assertFalse(event.getProcessed());
        assertTrue(event.getProcessingError()
                .contains("account application failed"));
        assertEquals(PaymentStatus.PENDING,
                paymentRepository.findById(payment.getId()).orElseThrow()
                        .getStatus());
    }

    @Test
    void invalidSignatureIsRejectedWithoutPersistingEvent() throws Exception {
        String reference = "invalid-signature-" + UUID.randomUUID();
        webhookReferences.add(reference);
        when(paystackConfig.getSecretKey()).thenReturn(WEBHOOK_SECRET);
        String payload = chargePayload(
                "charge.success", reference, 77001, "success", "7000");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.handleWebhook("invalid-signature", payload));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertTrue(webhookEventRepository.findByReference(reference).isEmpty());
    }

    @Test
    void concurrentDuplicateDeliveryWaitsForLockedEventAndAppliesOnce()
            throws Exception {
        Payment payment = createPendingPayment();
        String payload = chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                78001, "success", "7000");
        webhookReferences.add(payment.getIdempotencyKey());
        String signature = signature(payload);
        CountDownLatch firstAtApplication = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondProcessingAttempt = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        AtomicInteger applicationCalls = new AtomicInteger();
        AtomicInteger processingCalls = new AtomicInteger();

        doAnswer(invocation -> {
            if (processingCalls.incrementAndGet() == 2) {
                secondProcessingAttempt.countDown();
            }
            return invocation.callRealMethod();
        }).when(webhookEventProcessingService)
                .processIncomingEvent(any(UUID.class));
        doAnswer(invocation -> {
            if (applicationCalls.incrementAndGet() == 1) {
                firstAtApplication.countDown();
                if (!releaseFirst.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to release first payment application");
                }
            }
            return invocation.callRealMethod();
        }).when(tuitionAccountDomainService)
                .updateTuitionAccountAndProcessRegistration(payment.getId());

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() ->
                    paymentService.handleWebhook(signature, payload));
            assertTrue(firstAtApplication.await(30, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> {
                try {
                    paymentService.handleWebhook(signature, payload);
                } finally {
                    secondFinished.countDown();
                }
            });
            assertTrue(secondProcessingAttempt.await(30, TimeUnit.SECONDS));
            assertFalse(secondFinished.await(500, TimeUnit.MILLISECONDS));
            assertEquals(1, applicationCalls.get());
            assertEquals(0, tuitionAccountRepository
                    .findById(payment.getTuitionAccount().getId())
                    .orElseThrow().getTotalPaid().compareTo(BigDecimal.ZERO));

            releaseFirst.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);

            assertEquals(2, processingCalls.get());
            assertEquals(1, applicationCalls.get());

            List<WebhookEvent> events = webhookEventRepository
                    .findByReference(payment.getIdempotencyKey());
            assertEquals(1, events.size());
            assertTrue(events.get(0).getProcessed());
            assertEquals(new BigDecimal("70.00"),
                    tuitionAccountRepository.findById(
                                    payment.getTuitionAccount().getId())
                            .orElseThrow().getTotalPaid());
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void concurrentRetriesCannotApplySameEventTwice() throws Exception {
        Payment payment = createPendingPayment();
        deliver(chargePayload(
                "charge.success", payment.getIdempotencyKey(),
                79001, "success", "7000.5"));
        when(paystackClient.verifyTransaction(payment.getIdempotencyKey()))
                .thenReturn(Map.of("status", "success", "amount", 7000));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(
                    reconciliationJob::retryFailedWebhookEvent);
            Future<?> second = executor.submit(
                    reconciliationJob::retryFailedWebhookEvent);
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        }

        WebhookEvent event = webhookEventRepository
                .findByReference(payment.getIdempotencyKey()).get(0);
        assertTrue(event.getProcessed());
        assertEquals(PaymentStatus.VERIFIED,
                paymentRepository.findById(payment.getId()).orElseThrow()
                        .getStatus());
        assertEquals(new BigDecimal("70.00"),
                tuitionAccountRepository.findById(
                                payment.getTuitionAccount().getId())
                        .orElseThrow().getTotalPaid());
    }

    private Payment createPendingPayment() {
        AccountFixture fixture = createAccount();
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setTuitionAccountId(fixture.accountId());
        request.setAmount(new BigDecimal("70.00"));
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenReturn(Map.of(
                        "authorization_url", "https://example.test/authorize",
                        "access_code", "access-code"));

        InitiatePaymentResponse response = paymentService.initiatePayment(
                student(fixture), request);
        webhookReferences.add(response.getReference());
        return paymentRepository.findByIdempotencyKey(response.getReference())
                .orElseThrow();
    }

    private void deliver(String payload) {
        String reference = payloadReference(payload);
        webhookReferences.add(reference);
        paymentService.handleWebhook(signature(payload), payload);
    }

    private String chargePayload(
            String eventType,
            String reference,
            long providerTransactionId,
            String status,
            String amountKobo) {
        return "{\"event\":\"" + eventType + "\",\"data\":{"
                + "\"id\":" + providerTransactionId + ","
                + "\"reference\":\"" + reference + "\","
                + "\"status\":\"" + status + "\","
                + "\"amount\":" + amountKobo + "}}";
    }

    private String payloadReference(String payload) {
        int referenceStart = payload.indexOf("\"reference\":\"")
                + "\"reference\":\"".length();
        int referenceEnd = payload.indexOf('"', referenceStart);
        return payload.substring(referenceStart, referenceEnd);
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(
                    WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512"));
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to sign test webhook payload", exception);
        }
    }

    private AccountFixture createAccount() {
        String unique = UUID.randomUUID().toString();
        AccountFixture fixture = new TransactionTemplate(transactionManager)
                .execute(status -> {
                    _User student = userRepository.save(_User.builder()
                            .fullName("Task 20 Webhook Test")
                            .email("task20-" + unique + "@example.test")
                            .password("test-password")
                            .userRole(UserRole.STUDENT)
                            .isActive(true)
                            .isBanned(false)
                            .build());
                    Semester semester = semesterRepository.save(Semester.builder()
                            .academicYear("Task20-" + unique)
                            .semesterType(SemesterType.FIRST)
                            .status(SemesterStatus.ACTIVE)
                            .build());
                    Registration registration = registrationRepository.save(
                            Registration.builder()
                                    .student(student)
                                    .semester(semester)
                                    .status(RegistrationStatus.APPROVED)
                                    .totalCreditUnits(0)
                                    .build());
                    TuitionAccount account = tuitionAccountRepository.save(
                            TuitionAccount.builder()
                                    .registration(registration)
                                    .student(student)
                                    .semester(semester)
                                    .tuitionAmount(new BigDecimal("100.00"))
                                    .adminChargesTotal(BigDecimal.ZERO)
                                    .waiverAmount(BigDecimal.ZERO)
                                    .creditCarriedForward(BigDecimal.ZERO)
                                    .totalDue(new BigDecimal("100.00"))
                                    .totalPaid(BigDecimal.ZERO)
                                    .minimumPayment(new BigDecimal("70.00"))
                                    .balance(new BigDecimal("100.00"))
                                    .tuitionAccountStatus(
                                            TuitionAccountStatus.PENDING)
                                    .build());
                    return new AccountFixture(
                            account.getId(),
                            student.getId(),
                            registration.getId(),
                            semester.getId(),
                            student.getEmail());
                });
        assertNotNull(fixture);
        fixtures.add(fixture);
        return fixture;
    }

    private _User student(AccountFixture fixture) {
        return _User.builder()
                .id(fixture.studentId())
                .email(fixture.email())
                .userRole(UserRole.STUDENT)
                .build();
    }

    private record AccountFixture(
            UUID accountId,
            UUID studentId,
            UUID registrationId,
            UUID semesterId,
            String email) {
    }
}
