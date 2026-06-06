package com.kad.jet.security.service;

import com.kad.jet.security.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class TokenCleanUpScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanUpExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(new Date());
    }
}
