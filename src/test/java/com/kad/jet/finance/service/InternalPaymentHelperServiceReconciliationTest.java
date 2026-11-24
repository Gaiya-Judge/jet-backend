package com.kad.jet.finance.service;

import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.repository.PaymentRepository;
import com.kad.jet.notification.listener.NotificationEventListener;
import com.kad.jet.notification.service.NotificationService;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = InternalPaymentHelperServiceReconciliationTest.TestConfig.class)
class InternalPaymentHelperServiceReconciliationTest {

    @Autowired InternalPaymentHelperService paymentHelperService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired TuitionAccountDomainService tuitionAccountDomainService;
    @Autowired NotificationService notificationService;
    @Autowired UserRepository userRepository;
    @Autowired TestTransactionManager transactionManager;

    private final UUID paymentId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("70.00");
    private final AtomicBoolean notificationCreatedAfterCommit = new AtomicBoolean();
    private _User student;
    private Payment payment;

    @BeforeEach
    void setUp() {
        student = new _User();
        student.setId(studentId);
        payment = Payment.builder()
                .id(paymentId)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.PAYSTACK)
                .tuitionAccount(TuitionAccount.builder()
                        .id(accountId)
                        .student(student)
                        .build())
                .build();
        transactionManager.setCommitted(false);
        notificationCreatedAfterCommit.set(false);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        doAnswer(invocation -> {
            notificationCreatedAfterCommit.set(transactionManager.isCommitted());
            return null;
        }).when(notificationService).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void appliedReconciliationPaymentPublishesAndDeliversAfterCommit() {
        mockApplicationOutcome(true);

        paymentHelperService.processVerifiedPayment(payment, Map.of("amount", 7000));

        assertNotification("Your payment of ₦70.00 has been verified and applied to your tuition account.");
    }

    @Test
    void unappliedReconciliationPaymentPublishesAndDeliversAfterCommit() {
        mockApplicationOutcome(false);

        paymentHelperService.processVerifiedPayment(payment, Map.of("amount", 7000));

        assertNotification("Your payment of ₦70.00 has been verified but was not applied to your tuition account.");
    }

    private void mockApplicationOutcome(boolean applied) {
        when(tuitionAccountDomainService.updateTuitionAccountAndProcessRegistration(paymentId))
                .thenAnswer(invocation -> {
                    payment.setAppliedToAccount(applied);
                    return applied;
                });
    }

    private void assertNotification(String expectedMessage) {
        assertTrue(transactionManager.isCommitted());
        assertTrue(notificationCreatedAfterCommit.get());
        verify(paymentRepository).save(payment);
        verify(tuitionAccountDomainService).updateTuitionAccountAndProcessRegistration(paymentId);
        verify(notificationService).create(
                same(student),
                same(student),
                any(),
                eq("Payment Verified"),
                eq(expectedMessage),
                eq("TUITION_ACCOUNT"),
                eq(accountId));
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        TestTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        PaymentRepository paymentRepository() {
            return mock(PaymentRepository.class);
        }

        @Bean
        TuitionAccountDomainService tuitionAccountDomainService() {
            return mock(TuitionAccountDomainService.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        RegistrationRepository registrationRepository() {
            return mock(RegistrationRepository.class);
        }

        @Bean
        CourseRepository courseRepository() {
            return mock(CourseRepository.class);
        }

        @Bean
        InternalPaymentHelperService paymentHelperService(
                PaymentRepository paymentRepository,
                TuitionAccountDomainService tuitionAccountDomainService,
                ApplicationEventPublisher eventPublisher) {
            return new InternalPaymentHelperService(
                    paymentRepository, tuitionAccountDomainService, eventPublisher);
        }

        @Bean
        NotificationEventListener notificationEventListener(
                NotificationService notificationService,
                UserRepository userRepository,
                RegistrationRepository registrationRepository,
                CourseRepository courseRepository) {
            return new NotificationEventListener(
                    notificationService,
                    userRepository,
                    registrationRepository,
                    courseRepository);
        }
    }

    static class TestTransactionManager extends AbstractPlatformTransactionManager {
        private boolean committed;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            committed = false;
        }

        boolean isCommitted() {
            return committed;
        }

        void setCommitted(boolean committed) {
            this.committed = committed;
        }
    }
}