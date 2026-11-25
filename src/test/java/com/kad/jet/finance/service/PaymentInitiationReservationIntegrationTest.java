package com.kad.jet.finance.service;

import com.kad.jet.finance.dto.InitiatePaymentRequest;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentInitiationReservationIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private TuitionAccountDomainService tuitionAccountDomainService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TuitionAccountRepository tuitionAccountRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean private PaystackClient paystackClient;

    private final List<AccountFixture> fixtures = new ArrayList<>();

    @AfterEach
    void cleanUpFixtures() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            for (AccountFixture fixture : fixtures) {
                paymentRepository.deleteAll(
                        paymentRepository.findByTuitionAccountId(fixture.accountId()));
                tuitionAccountRepository.deleteById(fixture.accountId());
                registrationRepository.deleteById(fixture.registrationId());
                semesterRepository.deleteById(fixture.semesterId());
                userRepository.deleteById(fixture.studentId());
            }
        });
    }

    @Test
    void paystackReservationRejectsAmountAboveBalanceAfterActivePendingPayments() {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        addPendingPayment(fixture, new BigDecimal("30.00"),
                LocalDateTime.now().plusMinutes(10));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> initiatePaystack(fixture, new BigDecimal("71.00")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(paystackClient, never())
                .initializeTransaction(anyString(), any(), anyString());
    }

    @Test
    void expiredPendingPaymentDoesNotReducePaystackAvailableBalance() {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        addPendingPayment(fixture, new BigDecimal("50.00"),
                LocalDateTime.now().minusMinutes(1));
        mockSuccessfulPaystackInitialization();

        initiatePaystack(fixture, new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"),
                paymentRepository.sumActivePendingAmounts(
                        fixture.accountId(), LocalDateTime.now()));
    }

    @Test
    void amountWithNonZeroFractionalKoboIsRejectedBeforeReservation() {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> initiatePaystack(fixture, new BigDecimal("10.009")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(paymentRepository
                .findByTuitionAccountId(fixture.accountId()).isEmpty());
        verify(paystackClient, never())
                .initializeTransaction(anyString(), any(), anyString());
    }

    @Test
    void amountWithTrailingZeroPrecisionIsAccepted() {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        mockSuccessfulPaystackInitialization();

        initiatePaystack(fixture, new BigDecimal("10.000"));

        List<Payment> payments =
                paymentRepository.findByTuitionAccountId(fixture.accountId());
        assertEquals(1, payments.size());
        assertEquals(new BigDecimal("10.00"), payments.get(0).getAmount());
        assertEquals(PaymentStatus.PENDING, payments.get(0).getStatus());
    }

    @Test
    void connectionFailureDuringInitializationKeepsReservationPending() {
        AccountFixture fixture = createPaystackAccount();
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenThrow(new ResourceAccessException(
                        "connection refused",
                        new ConnectException("connection refused")));

        assertInitializationOutcomeUncertain(fixture);
    }

    @Test
    void timeoutDuringInitializationKeepsReservationPending() {
        AccountFixture fixture = createPaystackAccount();
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenThrow(new ResourceAccessException(
                        "read timed out",
                        new SocketTimeoutException("read timed out")));

        assertInitializationOutcomeUncertain(fixture);
    }

    @Test
    void serverErrorDuringInitializationKeepsReservationPending() {
        AccountFixture fixture = createPaystackAccount();
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.BAD_GATEWAY,
                        "Paystack server error"));

        assertInitializationOutcomeUncertain(fixture);
    }

    @Test
    void providerErrorEnvelopeDoesNotProveTransactionWasNotCreated() {
        AccountFixture fixture = createPaystackAccount();
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Paystack returned status false"));

        assertInitializationOutcomeUncertain(fixture);
    }

    @Test
    void malformedInitializationResponseKeepsReservationPending() {
        AccountFixture fixture = createPaystackAccount();
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenReturn(null);

        assertInitializationOutcomeUncertain(fixture);
    }

    @Test
    void manualVerificationTimeoutKeepsPersistedPaymentPending() {
        AccountFixture fixture = createPaystackAccount();
        mockSuccessfulPaystackInitialization();
        initiatePaystack(fixture, new BigDecimal("70.00"));
        Payment reservation = paymentRepository
                .findByTuitionAccountId(fixture.accountId())
                .get(0);
        when(paystackClient.verifyTransaction(
                reservation.getIdempotencyKey()))
                .thenThrow(new ResourceAccessException(
                        "read timed out",
                        new SocketTimeoutException("read timed out")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paymentService.verifyPaymentStatus(
                        student(fixture),
                        reservation.getIdempotencyKey()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        TransactionTemplate readTransaction =
                new TransactionTemplate(transactionManager);
        readTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Payment persistedPayment = readTransaction.execute(status ->
                paymentRepository.findById(reservation.getId())
                        .orElseThrow());
        assertNotNull(persistedPayment);
        assertEquals(PaymentStatus.PENDING, persistedPayment.getStatus());
    }

    @Test
    void concurrentSameAccountReservationsCannotExceedAvailableBalance()
            throws Exception {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        CountDownLatch pendingReservationSaved = new CountDownLatch(1);
        CountDownLatch competingTransactionReady = new CountDownLatch(1);
        CountDownLatch releaseAccountLock = new CountDownLatch(1);
        String competingApplicationName =
                "task17-" + UUID.randomUUID().toString().replace("-", "");
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> lockHolder = executor.submit(() ->
                    transaction.executeWithoutResult(status -> {
                        TuitionAccount account = tuitionAccountRepository
                                .findByIdWithLock(fixture.accountId())
                                .orElseThrow();
                        paymentRepository.save(Payment.builder()
                                .tuitionAccount(account)
                                .idempotencyKey(UUID.randomUUID().toString())
                                .appliedToAccount(false)
                                .amount(new BigDecimal("70.00"))
                                .paymentMethod(PaymentMethod.PAYSTACK)
                                .status(PaymentStatus.PENDING)
                                .paystackReference(UUID.randomUUID().toString())
                                .initiatedAt(LocalDateTime.now())
                                .expiresAt(LocalDateTime.now().plusMinutes(30))
                                .build());
                        pendingReservationSaved.countDown();
                        awaitLatch(releaseAccountLock);
                    }));

            assertTrue(pendingReservationSaved.await(10, TimeUnit.SECONDS));
            Future<Boolean> competingReservation = executor.submit(() -> {
                try {
                    return transaction.execute(status -> {
                        entityManager.createNativeQuery(
                                        "SELECT set_config('application_name', ?1, true)")
                                .setParameter(1, competingApplicationName)
                                .getSingleResult();
                        competingTransactionReady.countDown();
                        tuitionAccountDomainService.reservePaystackPayment(
                                student(fixture),
                                fixture.accountId(),
                                new BigDecimal("70.00"),
                                UUID.randomUUID().toString());
                        return true;
                    });
                } catch (ResponseStatusException exception) {
                    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
                    return false;
                }
            });
            assertTrue(competingTransactionReady.await(10, TimeUnit.SECONDS));
            awaitDatabaseLockWait(competingApplicationName, competingReservation);
            assertFalse(competingReservation.isDone());

            releaseAccountLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
            assertFalse(competingReservation.get(30, TimeUnit.SECONDS));
            assertEquals(new BigDecimal("70.00"),
                    paymentRepository.sumActivePendingAmounts(
                            fixture.accountId(), LocalDateTime.now()));
        } finally {
            releaseAccountLock.countDown();
        }
    }

    @Test
    void reservationForDifferentAccountCompletesWhileFirstAccountIsLocked()
            throws Exception {
        AccountFixture lockedFixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        AccountFixture reservationFixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        CountDownLatch accountLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> lockHolder = executor.submit(() ->
                    transaction.executeWithoutResult(status -> {
                        tuitionAccountRepository
                                .findByIdWithLock(lockedFixture.accountId())
                                .orElseThrow();
                        accountLocked.countDown();
                        awaitLatch(releaseLock);
                    }));

            assertTrue(accountLocked.await(10, TimeUnit.SECONDS));
            Future<Payment> otherAccountReservation = executor.submit(() ->
                    tuitionAccountDomainService.reservePaystackPayment(
                            student(reservationFixture),
                            reservationFixture.accountId(),
                            new BigDecimal("70.00"),
                            UUID.randomUUID().toString()));

            Payment payment = otherAccountReservation.get(5, TimeUnit.SECONDS);
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
            releaseLock.countDown();
            lockHolder.get(10, TimeUnit.SECONDS);
        } finally {
            releaseLock.countDown();
        }
    }

    @Test
    void waiverAdjustedMinimumPaymentIsStillEnforced() {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("40.00"),
                new BigDecimal("30.00"),
                TuitionAccountStatus.PENDING);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> initiatePaystack(fixture, new BigDecimal("39.00")));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        mockSuccessfulPaystackInitialization();
        initiatePaystack(fixture, new BigDecimal("40.00"));
    }

    @Test
    void remainingBalanceMustStillBePaidInFull() {
        AccountFixture fixture = createAccount(
                new BigDecimal("70.00"),
                new BigDecimal("30.00"),
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PARTIAL);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> initiatePaystack(fixture, new BigDecimal("69.00")));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        mockSuccessfulPaystackInitialization();
        initiatePaystack(fixture, new BigDecimal("70.00"));
    }

    @Test
    void alreadyPaidAccountRemainsRejected() {
        AccountFixture fixture = createAccount(
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PAID);

        ResponseStatusException paystackException = assertThrows(
                ResponseStatusException.class,
                () -> initiatePaystack(fixture, new BigDecimal("70.00")));
        assertEquals(HttpStatus.BAD_REQUEST, paystackException.getStatusCode());
    }

    @Test
    void paystackIsCalledAfterReservationTransactionHasCommitted() {
        AccountFixture fixture = createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
        TransactionTemplate readTransaction =
                new TransactionTemplate(transactionManager);
        readTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenAnswer(invocation -> {
                    assertFalse(TransactionSynchronizationManager
                            .isActualTransactionActive());
                    String reference = invocation.getArgument(2);
                    Payment reservation = readTransaction.execute(status ->
                            paymentRepository.findByIdempotencyKey(reference)
                                    .orElseThrow());
                    assertEquals(PaymentStatus.PENDING, reservation.getStatus());
                    assertEquals(PaymentMethod.PAYSTACK,
                            reservation.getPaymentMethod());
                    return Map.of(
                            "authorization_url", "https://example.test/authorize",
                            "access_code", "access-code");
                });

        initiatePaystack(fixture, new BigDecimal("70.00"));

        verify(paystackClient)
                .initializeTransaction(anyString(), any(), anyString());
    }

    private void awaitDatabaseLockWait(
            String applicationName,
            Future<Boolean> competingReservation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waitingForLock = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (" +
                            "SELECT 1 FROM pg_stat_activity " +
                            "WHERE application_name = ? " +
                            "AND state = 'active' " +
                            "AND wait_event_type = 'Lock' " +
                            "AND query ILIKE '%tuition_accounts%')",
                    Boolean.class,
                    applicationName);
            if (Boolean.TRUE.equals(waitingForLock)) {
                return;
            }
            if (competingReservation.isDone()) {
                throw new AssertionError(
                        "Competing reservation completed without waiting " +
                                "for the tuition-account database lock");
            }
            Thread.sleep(25);
        }
        throw new AssertionError(
                "Competing reservation did not reach the tuition-account database lock");
    }

    private void initiatePaystack(AccountFixture fixture, BigDecimal amount) {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setTuitionAccountId(fixture.accountId());
        request.setAmount(amount);
        paymentService.initiatePayment(student(fixture), request);
    }

    private AccountFixture createPaystackAccount() {
        return createAccount(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("70.00"),
                BigDecimal.ZERO,
                TuitionAccountStatus.PENDING);
    }

    private void assertInitializationOutcomeUncertain(AccountFixture fixture) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> initiatePaystack(fixture, new BigDecimal("70.00")));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode());
        List<Payment> payments =
                paymentRepository.findByTuitionAccountId(fixture.accountId());
        assertEquals(1, payments.size());
        Payment reservation = payments.get(0);
        assertEquals(PaymentStatus.PENDING, reservation.getStatus());
        assertNotNull(reservation.getPaystackReference());
        assertEquals(
                reservation.getIdempotencyKey(),
                reservation.getPaystackReference());
    }

    private void mockSuccessfulPaystackInitialization() {
        when(paystackClient.initializeTransaction(
                anyString(), any(), anyString()))
                .thenReturn(Map.of(
                        "authorization_url", "https://example.test/authorize",
                        "access_code", "access-code"));
    }

    private AccountFixture createAccount(
            BigDecimal balance,
            BigDecimal totalPaid,
            BigDecimal minimumPayment,
            BigDecimal waiverAmount,
            TuitionAccountStatus accountStatus) {
        String unique = UUID.randomUUID().toString();
        AccountFixture fixture = new TransactionTemplate(transactionManager)
                .execute(status -> {
                    _User student = userRepository.save(_User.builder()
                            .fullName("Task 17 Concurrency Test")
                            .email("task17-" + unique + "@example.test")
                            .password("test-password")
                            .userRole(UserRole.STUDENT)
                            .isActive(true)
                            .isBanned(false)
                            .build());
                    Semester semester = semesterRepository.save(Semester.builder()
                            .academicYear("Task17-" + unique)
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
                    BigDecimal totalDue = balance.add(totalPaid);
                    TuitionAccount account = tuitionAccountRepository.save(
                            TuitionAccount.builder()
                                    .registration(registration)
                                    .student(student)
                                    .semester(semester)
                                    .tuitionAmount(totalDue)
                                    .adminChargesTotal(BigDecimal.ZERO)
                                    .waiverAmount(waiverAmount)
                                    .creditCarriedForward(BigDecimal.ZERO)
                                    .totalDue(totalDue)
                                    .totalPaid(totalPaid)
                                    .minimumPayment(minimumPayment)
                                    .balance(balance)
                                    .tuitionAccountStatus(accountStatus)
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

    private void addPendingPayment(
            AccountFixture fixture,
            BigDecimal amount,
            LocalDateTime expiresAt) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    TuitionAccount account = tuitionAccountRepository
                            .findById(fixture.accountId())
                            .orElseThrow();
                    paymentRepository.save(Payment.builder()
                            .tuitionAccount(account)
                            .idempotencyKey(UUID.randomUUID().toString())
                            .appliedToAccount(false)
                            .amount(amount)
                            .paymentMethod(PaymentMethod.PAYSTACK)
                            .status(PaymentStatus.PENDING)
                            .paystackReference(UUID.randomUUID().toString())
                            .initiatedAt(LocalDateTime.now())
                            .expiresAt(expiresAt)
                            .build());
                });
    }

    private _User student(AccountFixture fixture) {
        return _User.builder()
                .id(fixture.studentId())
                .email(fixture.email())
                .userRole(UserRole.STUDENT)
                .build();
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release account lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding account lock", exception);
        }
    }

    private record AccountFixture(
            UUID accountId,
            UUID studentId,
            UUID registrationId,
            UUID semesterId,
            String email) {
    }
}
