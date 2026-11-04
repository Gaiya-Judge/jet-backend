package com.kad.jet.user.service;

import com.kad.jet.user.dto.ActivateAccountRequest;
import com.kad.jet.user.dto.ResendActivationRequest;
import com.kad.jet.user.model.ActivationIssuanceType;
import com.kad.jet.user.model.ActivationToken;
import com.kad.jet.user.model.EmailOutboxMessage;
import com.kad.jet.user.model._User;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.repository.ActivationTokenRepository;
import com.kad.jet.user.repository.EmailOutboxRepository;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountActivationServiceTest {

    @Mock private ActivationTokenRepository tokenRepository;
    @Mock private EmailOutboxRepository outboxRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ActivationPasswordPolicy passwordPolicy;
    @Mock private EntityManager entityManager;

    private ActivationPayloadCrypto payloadCrypto;
    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        payloadCrypto = new ActivationPayloadCrypto();
        ReflectionTestUtils.setField(payloadCrypto, "encodedKey",
                Base64.getEncoder().encodeToString(new byte[32]));
        payloadCrypto.initialize();

        service = new AccountActivationService(
                tokenRepository, outboxRepository, userRepository,
                payloadCrypto, passwordEncoder, passwordPolicy);
        ReflectionTestUtils.setField(service, "tokenLifetime", Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://frontend.example.test");
        ReflectionTestUtils.setField(service, "resendCooldown", Duration.ofSeconds(60));
        ReflectionTestUtils.setField(service, "resendWindow", Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "maximumResends", 5L);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void initialIssueStoresOnlyTokenHashAndEncryptsRawTokenInOutbox() {
        _User user = user();
        when(tokenRepository.save(any(ActivationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(EmailOutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issueInitialActivation(user);

        ArgumentCaptor<ActivationToken> tokenCaptor = ArgumentCaptor.forClass(ActivationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        ActivationToken token = tokenCaptor.getValue();
        ArgumentCaptor<EmailOutboxMessage> outboxCaptor = ArgumentCaptor.forClass(EmailOutboxMessage.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        EmailOutboxMessage message = outboxCaptor.getValue();

        String rawToken = payloadCrypto.decrypt(
                message.getEncryptedPayload(), token.getId() + ":" + message.getRecipient());
        assertEquals(43, rawToken.length());
        assertEquals(AccountActivationService.hashToken(rawToken), token.getTokenHash());
        assertNotEquals(rawToken, token.getTokenHash());
        assertEquals(token.getCreatedAt().plus(Duration.ofHours(24)), token.getExpiresAt());
        assertEquals(ActivationIssuanceType.INITIAL, token.getIssuanceType());
        assertEquals(user.getEmail(), message.getRecipient());
        assertSame(token, message.getActivationToken());
        assertFalse(new String(message.getEncryptedPayload(), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains(rawToken));
        assertThrows(IllegalStateException.class, () -> payloadCrypto.decrypt(
                message.getEncryptedPayload(), token.getId() + ":different-recipient@example.test"));
        verify(tokenRepository).revokeOutstandingByUserId(eq(user.getId()), any(Instant.class));
    }

    @Test
    void pendingPasswordHashUsesFreshUndisclosedRandomCredential() {
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-pending-hash");

        String passwordHash = service.createPendingPasswordHash();

        assertEquals("bcrypt-pending-hash", passwordHash);
        ArgumentCaptor<String> generated = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(generated.capture());
        assertEquals(43, generated.getValue().length());
        assertTrue(generated.getValue().matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void validActivationSetsEncodedPasswordEnablesAccountAndConsumesToken() {
        _User user = user();
        String rawToken = "high-entropy-activation-token";
        ActivationToken token = token(user, rawToken, Instant.now().plusSeconds(3600));
        when(tokenRepository.findByTokenHash(AccountActivationService.hashToken(rawToken)))
                .thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findByTokenHashForUpdate(AccountActivationService.hashToken(rawToken)))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("ValidPassword2026"))
                .thenReturn("$2a$12$encoded-password-hash");

        service.activate(activationRequest(rawToken, "ValidPassword2026", "ValidPassword2026"));

        assertTrue(user.isEnabled());
        assertEquals("$2a$12$encoded-password-hash", user.getPassword());
        assertNotNull(token.getConsumedAt());
        verify(passwordPolicy).validate("ValidPassword2026");
        verify(entityManager).flush();
        verify(tokenRepository).revokeOutstandingByUserId(eq(user.getId()), any(Instant.class));
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void passwordConfirmationMismatchDoesNotLookUpOrMutateToken() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.activate(activationRequest("token", "ValidPassword2026", "DifferentPassword2026")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(tokenRepository, userRepository, passwordEncoder, entityManager);
    }

    @Test
    void activationTokenLookupUsesDatabasePessimisticWriteLock() throws Exception {
        Lock lock = ActivationTokenRepository.class
                .getMethod("findByTokenHashForUpdate", String.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void invalidExpiredConsumedAndRevokedTokensAreRejectedWithoutChangingUser() {
        _User user = user();
        String rawToken = "activation-token";
        String hash = AccountActivationService.hashToken(rawToken);
        when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());
        assertRejected(rawToken);

        for (ActivationToken invalid : new ActivationToken[]{
                token(user, rawToken, Instant.now().minusSeconds(1)),
                consumedToken(user, rawToken),
                revokedToken(user, rawToken)}) {
            when(tokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(invalid));
            when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
            when(tokenRepository.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(invalid));
            assertRejected(rawToken);
        }

        assertFalse(user.isEnabled());
        assertEquals("pending-hash", user.getPassword());
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder, entityManager);
    }

    @Test
    void resendRevokesOutstandingTokensAndCreatesReplacementOutboxMessage() {
        _User user = user();
        ResendActivationRequest request = new ResendActivationRequest();
        request.setEmail(user.getEmail());
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(tokenRepository.countByUser_IdAndIssuanceTypeAndCreatedAtAfter(
                eq(user.getId()), eq(ActivationIssuanceType.RESEND), any(Instant.class))).thenReturn(0L);
        when(tokenRepository.save(any(ActivationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(EmailOutboxMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.resend(request);

        verify(tokenRepository).revokeOutstandingByUserId(eq(user.getId()), any(Instant.class));
        ArgumentCaptor<ActivationToken> tokenCaptor = ArgumentCaptor.forClass(ActivationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        assertEquals(ActivationIssuanceType.RESEND, tokenCaptor.getValue().getIssuanceType());
        verify(outboxRepository).save(any(EmailOutboxMessage.class));
    }

    @Test
    void resendCooldownAndDailyLimitSuppressNewTokens() {
        _User user = user();
        ResendActivationRequest request = new ResendActivationRequest();
        request.setEmail(user.getEmail());
        when(userRepository.findByEmailIgnoreCaseForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(tokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(ActivationToken.builder().createdAt(Instant.now()).build()));
        service.resend(request);
        verify(tokenRepository, never()).save(any());

        when(tokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(ActivationToken.builder().createdAt(Instant.now().minusSeconds(61)).build()));
        when(tokenRepository.countByUser_IdAndIssuanceTypeAndCreatedAtAfter(
                eq(user.getId()), eq(ActivationIssuanceType.RESEND), any(Instant.class))).thenReturn(5L);
        service.resend(request);
        verify(tokenRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    private void assertRejected(String rawToken) {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.activate(activationRequest(rawToken, "ValidPassword2026", "ValidPassword2026")));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private _User user() {
        return _User.builder()
                .id(UUID.randomUUID())
                .email("user@example.test")
                .fullName("Activation User")
                .userRole(UserRole.REGISTRAR)
                .password("pending-hash")
                .isActive(false)
                .isBanned(false)
                .build();
    }

    private ActivationToken token(_User user, String rawToken, Instant expiresAt) {
        return ActivationToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(AccountActivationService.hashToken(rawToken))
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .issuanceType(ActivationIssuanceType.INITIAL)
                .build();
    }

    private ActivationToken consumedToken(_User user, String rawToken) {
        ActivationToken token = token(user, rawToken, Instant.now().plusSeconds(3600));
        token.setConsumedAt(Instant.now());
        return token;
    }

    private ActivationToken revokedToken(_User user, String rawToken) {
        ActivationToken token = token(user, rawToken, Instant.now().plusSeconds(3600));
        token.setRevokedAt(Instant.now());
        return token;
    }

    private ActivateAccountRequest activationRequest(String token, String password, String confirmation) {
        ActivateAccountRequest request = new ActivateAccountRequest();
        request.setToken(token);
        request.setPassword(password);
        request.setPasswordConfirmation(confirmation);
        return request;
    }
}
