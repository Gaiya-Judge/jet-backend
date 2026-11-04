package com.kad.jet.user.service;

import com.kad.jet.user.model.ActivationToken;
import com.kad.jet.user.model.EmailOutboxMessage;
import com.kad.jet.user.model.EmailOutboxStatus;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.ActivationTokenRepository;
import com.kad.jet.user.repository.EmailOutboxRepository;
import jakarta.persistence.JoinColumn;
import org.springframework.data.jpa.repository.Lock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

    @Mock private EmailOutboxRepository outboxRepository;
    @Mock private ActivationTokenRepository tokenRepository;
    @Mock private ActivationPayloadCrypto payloadCrypto;
    @Mock private ActivationEmailSender emailSender;

    private EmailOutboxClaimService claimService;
    private EmailOutboxStateService stateService;
    private EmailOutboxDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        claimService = new EmailOutboxClaimService(outboxRepository);
        ReflectionTestUtils.setField(claimService, "maximumAttempts", 8);

        stateService = new EmailOutboxStateService(outboxRepository);
        ReflectionTestUtils.setField(stateService, "maximumAttempts", 8);
        ReflectionTestUtils.setField(stateService, "initialRetryDelay", Duration.ofSeconds(30));
        ReflectionTestUtils.setField(stateService, "maximumRetryDelay", Duration.ofHours(6));

        deliveryService = new EmailOutboxDeliveryService(
                outboxRepository, tokenRepository, payloadCrypto, emailSender, stateService);
    }

    @Test
    void claimMarksMessageProcessingAndIncrementsAttempt() {
        EmailOutboxMessage message = message(EmailOutboxStatus.PENDING, 0);
        when(outboxRepository.findClaimable(any(), any(), any(Instant.class), any()))
                .thenReturn(List.of(message));

        List<UUID> claimed = claimService.claimBatch(10);

        assertEquals(List.of(message.getId()), claimed);
        assertEquals(EmailOutboxStatus.PROCESSING, message.getStatus());
        assertEquals(1, message.getAttemptCount());
        assertNotNull(message.getLeaseUntil());
    }

    @Test
    void outboxClaimsUseDatabasePessimisticWriteLock() throws Exception {
        Lock lock = EmailOutboxRepository.class
                .getMethod("findClaimable", EmailOutboxStatus.class,
                        EmailOutboxStatus.class, Instant.class,
                        org.springframework.data.domain.Pageable.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void activationTokenRelationshipRequiresOneOutboxMessagePerToken() throws Exception {
        JoinColumn joinColumn = EmailOutboxMessage.class
                .getDeclaredField("activationToken")
                .getAnnotation(JoinColumn.class);

        assertNotNull(joinColumn);
        assertEquals("activation_token_id", joinColumn.name());
        assertTrue(joinColumn.unique());
    }

    @Test
    void expiredLeaseAtAttemptLimitBecomesPermanentFailure() {
        EmailOutboxMessage message = message(EmailOutboxStatus.PROCESSING, 8);
        message.setLeaseUntil(Instant.now().minusSeconds(1));
        when(outboxRepository.findClaimable(any(), any(), any(Instant.class), any()))
                .thenReturn(List.of(message));

        assertTrue(claimService.claimBatch(10).isEmpty());
        assertEquals(EmailOutboxStatus.FAILED, message.getStatus());
        assertEquals("PROCESSING_LEASE_EXPIRED_MAX_ATTEMPTS", message.getLastErrorCode());
    }

    @Test
    void temporaryFailureSchedulesExponentialRetryAndEventuallyFails() {
        EmailOutboxMessage message = message(EmailOutboxStatus.PROCESSING, 2);
        when(outboxRepository.findById(message.getId())).thenReturn(Optional.of(message));
        Instant beforeRetry = Instant.now();

        stateService.markFailed(message.getId(), false, "SMTP_TEMPORARY_FAILURE");

        assertEquals(EmailOutboxStatus.PENDING, message.getStatus());
        assertEquals("SMTP_TEMPORARY_FAILURE", message.getLastErrorCode());
        assertTrue(message.getNextAttemptAt().isAfter(beforeRetry.plusSeconds(59)));
        assertTrue(message.getNextAttemptAt().isBefore(beforeRetry.plusSeconds(62)));

        message.setStatus(EmailOutboxStatus.PROCESSING);
        message.setAttemptCount(8);
        stateService.markFailed(message.getId(), false, "SMTP_TEMPORARY_FAILURE");
        assertEquals(EmailOutboxStatus.FAILED, message.getStatus());
    }

    @Test
    void permanentFailureIsIdentifiedImmediately() {
        EmailOutboxMessage message = message(EmailOutboxStatus.PROCESSING, 1);
        when(outboxRepository.findById(message.getId())).thenReturn(Optional.of(message));

        stateService.markFailed(message.getId(), true, "SMTP_PERMANENT_FAILURE");

        assertEquals(EmailOutboxStatus.FAILED, message.getStatus());
        assertEquals("SMTP_PERMANENT_FAILURE", message.getLastErrorCode());
    }

    @Test
    void pendingEmailIsSentOnlyWhileItsActivationTokenRemainsValid() {
        EmailOutboxMessage message = message(EmailOutboxStatus.PROCESSING, 1);
        when(outboxRepository.findWithActivationTokenById(message.getId())).thenReturn(Optional.of(message));
        when(tokenRepository.existsByIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                eq(message.getActivationToken().getId()), any(Instant.class))).thenReturn(true);
        when(payloadCrypto.decrypt(message.getEncryptedPayload(),
                message.getActivationToken().getId() + ":" + message.getRecipient()))
                .thenReturn("raw-activation-token");
        when(outboxRepository.findById(message.getId())).thenReturn(Optional.of(message));

        deliveryService.deliver(message.getId());

        verify(emailSender).sendActivationEmail("recipient@example.test", "raw-activation-token");
        assertEquals(EmailOutboxStatus.SENT, message.getStatus());
        assertNotNull(message.getDeliveredAt());
    }

    @Test
    void queuedEmailForRevokedTokenIsCancelledWithoutSending() {
        EmailOutboxMessage message = message(EmailOutboxStatus.PROCESSING, 1);
        when(outboxRepository.findWithActivationTokenById(message.getId())).thenReturn(Optional.of(message));
        when(tokenRepository.existsByIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
                eq(message.getActivationToken().getId()), any(Instant.class))).thenReturn(false);
        when(outboxRepository.findById(message.getId())).thenReturn(Optional.of(message));

        deliveryService.deliver(message.getId());

        assertEquals(EmailOutboxStatus.CANCELLED, message.getStatus());
        verifyNoInteractions(emailSender);
        verifyNoInteractions(payloadCrypto);
    }

    private EmailOutboxMessage message(EmailOutboxStatus status, int attempts) {
        ActivationToken activationToken = ActivationToken.builder()
                .id(UUID.randomUUID())
                .user(_User.builder().id(UUID.randomUUID()).email("recipient@example.test").build())
                .build();
        return EmailOutboxMessage.builder()
                .id(UUID.randomUUID())
                .recipient("recipient@example.test")
                .activationToken(activationToken)
                .encryptedPayload(new byte[]{1, 2, 3})
                .status(status)
                .attemptCount(attempts)
                .nextAttemptAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
