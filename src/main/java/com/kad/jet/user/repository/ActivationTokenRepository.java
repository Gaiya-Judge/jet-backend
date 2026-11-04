package com.kad.jet.user.repository;

import com.kad.jet.user.model.ActivationIssuanceType;
import com.kad.jet.user.model.ActivationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {

    Optional<ActivationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ActivationToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<ActivationToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("update ActivationToken t set t.revokedAt = :revokedAt " +
            "where t.user.id = :userId and t.consumedAt is null and t.revokedAt is null")
    int revokeOutstandingByUserId(@Param("userId") UUID userId,
                                  @Param("revokedAt") Instant revokedAt);

    long countByUser_IdAndIssuanceTypeAndCreatedAtAfter(
            UUID userId, ActivationIssuanceType issuanceType, Instant createdAt);

    Optional<ActivationToken> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);

    boolean existsByIdAndConsumedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
            UUID id, Instant now);
}
