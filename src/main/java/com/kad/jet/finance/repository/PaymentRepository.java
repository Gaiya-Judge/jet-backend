package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {


    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByTuitionAccountId(UUID tuitionAccountId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByPaymentMethodAndStatus(
            PaymentMethod paymentMethod,
            PaymentStatus status,
            Pageable pageable);

    // Find non-expired pending payments for an account
    @Query("SELECT p FROM Payment p " +
            "WHERE p.tuitionAccount.id = :accountId " +
            "AND p.status = 'PENDING' " +
            "AND p.expiresAt > :now")
    List<Payment> findActivePendingPayments(
            @Param("accountId") UUID accountId,
            @Param("now") LocalDateTime now);

    // Sum of all non-expired pending amounts for an account
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.tuitionAccount.id = :accountId " +
            "AND p.status = 'PENDING' " +
            "AND p.expiresAt > :now")
    BigDecimal sumActivePendingAmounts(
            @Param("accountId") UUID accountId,
            @Param("now") LocalDateTime now);

    // Find expired pending payments — used by cleanup job
    @Query("SELECT p FROM Payment p " +
            "WHERE p.status = 'PENDING' " +
            "AND p.expiresAt < :now")
    List<Payment> findExpiredPendingPayments(
            @Param("now") LocalDateTime now);
}
