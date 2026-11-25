package com.kad.jet.user.service;

import com.kad.jet.security.model.RefreshToken;
import com.kad.jet.security.repository.RefreshTokenRepository;
import com.kad.jet.security.service.JwtUtil;
import com.kad.jet.user.dto.RefreshTokenRequest;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RefreshTokenRotationConcurrencyIntegrationTest {

    @Autowired private UserAuthService userAuthService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenExpiration;

    private UUID fixtureUserId;

    @BeforeEach
    void configureJwtUtil() {
        JwtUtil util = new JwtUtil();
        util.setSecretKey(jwtSecret);
        util.setJwtAccessKeyExpiration(accessTokenExpiration);
        util.setJwtRefreshTokenKeyExpiration(refreshTokenExpiration);
    }

    @AfterEach
    void removeFixture() {
        if (fixtureUserId == null) {
            return;
        }

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            List<RefreshToken> tokensForUser = refreshTokenRepository
                    .findAll().stream()
                    .filter(token -> token.getUser().getId()
                            .equals(fixtureUserId))
                    .toList();
            refreshTokenRepository.deleteAll(tokensForUser);
            userRepository.deleteById(fixtureUserId);
        });
    }

    @Test
    void simultaneousRefreshRequestsRotateStoredTokenOnlyOnce()
            throws Exception {
        RefreshFixture fixture = createRefreshFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<RefreshOutcome> first = executor.submit(
                    () -> refreshAtSameTime(fixture.token(), ready, start));
            Future<RefreshOutcome> second = executor.submit(
                    () -> refreshAtSameTime(fixture.token(), ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<RefreshOutcome> outcomes = List.of(
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream()
                    .filter(outcome -> outcome == RefreshOutcome.SUCCESS)
                    .count());
            assertEquals(1, outcomes.stream()
                    .filter(outcome -> outcome == RefreshOutcome.REJECTED)
                    .count());
        } finally {
            start.countDown();
        }

        RefreshToken oldToken = refreshTokenRepository
                .findByToken(fixture.token()).orElseThrow();
        assertTrue(oldToken.getIsRevoked());

        List<RefreshToken> tokensForUser = new TransactionTemplate(
                transactionManager).execute(status ->
                refreshTokenRepository.findAll().stream()
                        .filter(token -> token.getUser().getId()
                                .equals(fixture.userId()))
                        .toList());
        assertNotNull(tokensForUser);
        assertEquals(2, tokensForUser.size());
        assertEquals(1, tokensForUser.stream()
                .filter(token -> !token.getIsRevoked())
                .count());
    }

    private RefreshFixture createRefreshFixture() {
        String email = "refresh-race-" + UUID.randomUUID() + "@example.test";
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);
        RefreshFixture fixture = transaction.execute(status -> {
            _User user = userRepository.save(_User.builder()
                    .email(email)
                    .fullName("Refresh Token Concurrency Test")
                    .password("unused-test-password")
                    .userRole(UserRole.STUDENT)
                    .isActive(true)
                    .isBanned(false)
                    .build());
            String token = JwtUtil.generateRefreshToken(user);
            refreshTokenRepository.save(RefreshToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(JwtUtil.extractExpiration(token))
                    .isRevoked(false)
                    .build());
            fixtureUserId = user.getId();
            return new RefreshFixture(user.getId(), token);
        });
        assertNotNull(fixture);
        return fixture;
    }

    private RefreshOutcome refreshAtSameTime(
            String token,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Timed out waiting to start concurrent refresh");
        }

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(token);
        try {
            userAuthService.refreshToken(request);
            return RefreshOutcome.SUCCESS;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.UNAUTHORIZED) {
                throw exception;
            }
            return RefreshOutcome.REJECTED;
        }
    }

    private enum RefreshOutcome {
        SUCCESS,
        REJECTED
    }

    private record RefreshFixture(UUID userId, String token) {
    }
}
