package com.kad.jet.user.repository;

import com.kad.jet.user.model.EmailOutboxMessage;
import com.kad.jet.user.model.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    @Query("select m from EmailOutboxMessage m join fetch m.activationToken where m.id = :id")
    java.util.Optional<EmailOutboxMessage> findWithActivationTokenById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from EmailOutboxMessage m where " +
            "(m.status = :pending and m.nextAttemptAt <= :now) or " +
            "(m.status = :processing and m.leaseUntil <= :now) " +
            "order by m.createdAt")
    List<EmailOutboxMessage> findClaimable(
            @Param("pending") EmailOutboxStatus pending,
            @Param("processing") EmailOutboxStatus processing,
            @Param("now") Instant now,
            Pageable pageable);
}
