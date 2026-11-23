package com.kad.jet.finance.service;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TuitionAccountDomainServiceRegistrationCompletionTest {

    @Mock TuitionAccountRepository tuitionAccountRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock EntityManager entityManager;

    @InjectMocks TuitionAccountDomainService service;

    private Registration registration;
    private TuitionAccount account;
    private Payment payment;

    @BeforeEach
    void setUp() {
        registration = Registration.builder().id(UUID.randomUUID())
                .status(RegistrationStatus.APPROVED).build();
        account = TuitionAccount.builder().id(UUID.randomUUID())
                .registration(registration)
                .totalDue(new BigDecimal("100.00"))
                .totalPaid(BigDecimal.ZERO)
                .minimumPayment(new BigDecimal("70.00"))
                .balance(new BigDecimal("100.00"))
                .tuitionAccountStatus(TuitionAccountStatus.PENDING).build();
        payment = Payment.builder().id(UUID.randomUUID())
                .amount(new BigDecimal("70.00"))
                .appliedToAccount(false)
                .paystackReference("payment-reference")
                .tuitionAccount(account).build();
        when(paymentRepository.findByIdWithLock(payment.getId()))
                .thenReturn(Optional.of(payment));
        lenient().when(tuitionAccountRepository.findByRegistrationIdWithLock(registration.getId()))
                .thenReturn(Optional.of(account));
    }

    @Test
    void reachingMinimumPaymentCompletesApprovedRegistrationWithoutFullPayment() {
        boolean appliedToAccount =
            service.updateTuitionAccountAndProcessRegistration(payment.getId());

        assertTrue(appliedToAccount);
        assertEquals(TuitionAccountStatus.PARTIAL, account.getTuitionAccountStatus());
        assertEquals(new BigDecimal("70.00"), account.getTotalPaid());
        assertTrue(payment.getAppliedToAccount());
        assertEquals(RegistrationStatus.COMPLETED, registration.getStatus());
        verify(tuitionAccountRepository).save(account);
        verify(registrationRepository).save(registration);
        verify(paymentRepository).save(payment);
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"DRAFT", "PENDING_DEAN"})
    void paymentThresholdDoesNotCompleteRegistrationBeforeApproval(RegistrationStatus status) {
        registration.setStatus(status);

        service.updateTuitionAccountAndProcessRegistration(payment.getId());

        assertEquals(status, registration.getStatus());
        verify(registrationRepository, never()).save(any());
        assertEquals(TuitionAccountStatus.PARTIAL, account.getTuitionAccountStatus());
    }

    @Test
    void repeatedApplicationDoesNotUpdateAccountAgain() {
        payment.setAppliedToAccount(true);

        boolean appliedToAccount =
            service.updateTuitionAccountAndProcessRegistration(payment.getId());

        assertTrue(appliedToAccount);
        assertEquals(BigDecimal.ZERO, account.getTotalPaid());
        verify(tuitionAccountRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(entityManager);
    }

    @Test
    void alreadyPaidAccountDoesNotChangeTotalsOrMarkPaymentApplied() {
        account.setTuitionAccountStatus(TuitionAccountStatus.PAID);
        account.setTotalPaid(new BigDecimal("100.00"));
        account.setBalance(BigDecimal.ZERO);

        boolean appliedToAccount =
            service.updateTuitionAccountAndProcessRegistration(payment.getId());

        assertFalse(appliedToAccount);
        assertEquals(new BigDecimal("100.00"), account.getTotalPaid());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(TuitionAccountStatus.PAID, account.getTuitionAccountStatus());
        assertFalse(payment.getAppliedToAccount());
        verify(tuitionAccountRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paymentCalculationsUseTuitionAccountStateRefreshedAfterLock() {
        doAnswer(invocation -> {
            TuitionAccount refreshedAccount = invocation.getArgument(0);
            refreshedAccount.setTotalDue(new BigDecimal("140.00"));
            refreshedAccount.setTotalPaid(new BigDecimal("50.00"));
            refreshedAccount.setMinimumPayment(new BigDecimal("100.00"));
            refreshedAccount.setTuitionAccountStatus(TuitionAccountStatus.PENDING);
            return null;
        }).when(entityManager).refresh(account, LockModeType.PESSIMISTIC_WRITE);

        service.updateTuitionAccountAndProcessRegistration(payment.getId());

        assertEquals(new BigDecimal("120.00"), account.getTotalPaid());
        assertEquals(new BigDecimal("20.00"), account.getBalance());
        assertEquals(TuitionAccountStatus.PARTIAL, account.getTuitionAccountStatus());
        assertEquals(RegistrationStatus.COMPLETED, registration.getStatus());
        verify(tuitionAccountRepository).save(account);
        verify(registrationRepository).save(registration);
    }

    @Test
    void alreadyCompletedRegistrationStaysCompletedWithoutRepeatedCompletionWrite() {
        registration.setStatus(RegistrationStatus.COMPLETED);

        service.updateTuitionAccountAndProcessRegistration(payment.getId());

        assertEquals(RegistrationStatus.COMPLETED, registration.getStatus());
        verify(registrationRepository, never()).save(any());
        verify(tuitionAccountRepository).save(account);
    }

    @Test
    void paymentProcessingLocksTuitionAccountBeforeUpdatingAccount() {
        when(tuitionAccountRepository.findByRegistrationIdWithLock(registration.getId()))
                .thenReturn(Optional.of(account));

        service.updateTuitionAccountAndProcessRegistration(payment.getId());

        var invocationOrder = inOrder(tuitionAccountRepository, entityManager);
        invocationOrder.verify(tuitionAccountRepository)
            .findByRegistrationIdWithLock(registration.getId());
        invocationOrder.verify(entityManager)
            .refresh(account, LockModeType.PESSIMISTIC_WRITE);
        verify(tuitionAccountRepository).save(account);
    }
}
