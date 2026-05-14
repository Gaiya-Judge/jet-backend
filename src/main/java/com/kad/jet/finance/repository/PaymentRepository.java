package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.Payment;
import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
