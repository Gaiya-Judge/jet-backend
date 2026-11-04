package com.kad.jet.user.service;

import com.kad.jet.user.dto.ActivateAccountRequest;
import com.kad.jet.user.dto.ResendActivationRequest;
import com.kad.jet.user.model.ActivationIssuanceType;
import com.kad.jet.user.model.ActivationToken;
import com.kad.jet.user.model.EmailOutboxMessage;
import com.kad.jet.user.model.EmailOutboxMessageType;
import com.kad.jet.user.model.EmailOutboxStatus;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.ActivationTokenRepository;
import com.kad.jet.user.repository.EmailOutboxRepository;
import com.kad.jet.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountActivationService {

    private static final String INVALID_TOKEN = "Activation token is invalid or expired";

    private final ActivationTokenRepository activationTokenRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final UserRepository userRepository;
    private final ActivationPayloadCrypto payloadCrypto;
    private final PasswordEncoder passwordEncoder;
    private final ActivationPasswordPolicy passwordPolicy;
    private final SecureRandom secureRandom = new SecureRandom();

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${activation.token.expiration:PT24H}")
    private Duration tokenLifetime;

    @Value("${activation.frontend-base-url:}")
    private String frontendBaseUrl;

    @Value("${activation.resend.cooldown:PT60S}")
    private Duration resendCooldown;

    @Value("${activation.resend.window:PT24H}")
    private Duration resendWindow;

    @Value("${activation.resend.maximum:5}")
    private long maximumResends;

    @Transactional
    public void issueInitialActivation(_User user) {
        issue(user, ActivationIssuanceType.INITIAL);
    }

    public String createPendingPasswordHash() {
        byte[] randomPassword = new byte[32];
        secureRandom.nextBytes(randomPassword);
        String undisclosedPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(randomPassword);
        return passwordEncoder.encode(undisclosedPassword);
    }

    @Transactional
    public void resend(ResendActivationRequest request) {
        userRepository.findByEmailIgnoreCaseForUpdate(request.getEmail().trim())
                .filter(user -> !user.isEnabled() && user.isAccountNonLocked())
                .ifPresent(user -> issueResendIfAllowed(user));
    }

    @Transactional
    public void activate(ActivateAccountRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }
        passwordPolicy.validate(request.getPassword());

        String tokenHash = hashToken(request.getToken());
        ActivationToken tokenReference = activationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN));
        _User user = userRepository.findByIdForUpdate(tokenReference.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN));
        ActivationToken token = activationTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN));
        Instant now = Instant.now();
        if (token.getConsumedAt() != null || token.getRevokedAt() != null
                || !token.getExpiresAt().isAfter(now) || user.isEnabled()
                || !token.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_TOKEN);
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(true);
        user.setUpdateAt(java.time.LocalDateTime.now());
        token.setConsumedAt(now);
        entityManager.flush();
        activationTokenRepository.revokeOutstandingByUserId(user.getId(), now);
        // The current token is already marked consumed and is excluded by the update.
        activationTokenRepository.save(token);
        userRepository.save(user);
    }

    private void issueResendIfAllowed(_User user) {
        Instant now = Instant.now();
        var latest = activationTokenRepository.findFirstByUser_IdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent() && latest.get().getCreatedAt().plus(resendCooldown).isAfter(now)) {
            return;
        }
        long sentInWindow = activationTokenRepository.countByUser_IdAndIssuanceTypeAndCreatedAtAfter(
                user.getId(), ActivationIssuanceType.RESEND, now.minus(resendWindow));
        if (sentInWindow >= maximumResends) {
            return;
        }
        issue(user, ActivationIssuanceType.RESEND);
    }

    private void issue(_User user, ActivationIssuanceType issuanceType) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalStateException("Activation frontend base URL is not configured");
        }
        Instant now = Instant.now();
        activationTokenRepository.revokeOutstandingByUserId(user.getId(), now);

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        ActivationToken activationToken = activationTokenRepository.save(ActivationToken.builder()
                .user(user)
                .tokenHash(hashToken(rawToken))
                .expiresAt(now.plus(tokenLifetime))
                .issuanceType(issuanceType)
                .createdAt(now)
                .build());

        EmailOutboxMessage message = EmailOutboxMessage.builder()
                .recipient(user.getEmail())
                .messageType(EmailOutboxMessageType.ACCOUNT_ACTIVATION)
                .encryptedPayload(payloadCrypto.encrypt(
                        rawToken,
                        activationToken.getId() + ":" + user.getEmail()))
                .activationToken(activationToken)
                .status(EmailOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();
        emailOutboxRepository.save(message);
    }

    static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Token hashing is unavailable");
        }
    }
}
