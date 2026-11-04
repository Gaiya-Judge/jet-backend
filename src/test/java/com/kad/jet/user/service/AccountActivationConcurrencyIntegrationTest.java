package com.kad.jet.user.service;

import com.kad.jet.user.dto.ActivateAccountRequest;
import com.kad.jet.user.model.ActivationIssuanceType;
import com.kad.jet.user.model.ActivationToken;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.ActivationTokenRepository;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AccountActivationConcurrencyIntegrationTest {

    @Autowired private AccountActivationService activationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivationTokenRepository tokenRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentActivationRequestsCanConsumeTokenOnlyOnce() throws Exception {
        String rawToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String email = "activation-" + UUID.randomUUID() + "@example.test";
        UUID[] ids = new UUID[2];
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            _User user = userRepository.save(_User.builder()
                    .email(email)
                    .fullName("Concurrent Activation Test")
                    .password("pending-hash")
                    .userRole(UserRole.REGISTRAR)
                    .isActive(false)
                    .isBanned(false)
                    .build());
            ActivationToken token = tokenRepository.save(ActivationToken.builder()
                    .user(user)
                    .tokenHash(AccountActivationService.hashToken(rawToken))
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .issuanceType(ActivationIssuanceType.INITIAL)
                    .createdAt(Instant.now())
                    .build());
            ids[0] = user.getId();
            ids[1] = token.getId();
        });

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> activateAtOnce(ready, start, rawToken));
            var second = executor.submit(() -> activateAtOnce(ready, start, rawToken));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successes = (first.get(30, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(30, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertTrue(userRepository.findById(ids[0]).orElseThrow().isEnabled());
            assertNotNull(tokenRepository.findById(ids[1]).orElseThrow().getConsumedAt());
        } finally {
            transaction.executeWithoutResult(status -> {
                tokenRepository.deleteById(ids[1]);
                userRepository.deleteById(ids[0]);
            });
        }
    }

    private boolean activateAtOnce(CountDownLatch ready, CountDownLatch start, String rawToken)
            throws InterruptedException {
        ready.countDown();
        start.await();
        ActivateAccountRequest request = new ActivateAccountRequest();
        request.setToken(rawToken);
        request.setPassword("ConcurrentPassword2026");
        request.setPasswordConfirmation("ConcurrentPassword2026");
        try {
            activationService.activate(request);
            return true;
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
            return false;
        }
    }
}
