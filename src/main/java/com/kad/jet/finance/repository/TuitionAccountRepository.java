package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.TuitionAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TuitionAccountRepository extends JpaRepository<TuitionAccount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TuitionAccount t WHERE t.registration.id = :registrationId")
    Optional<TuitionAccount> findByRegistrationIdWithLock(@Param("registrationId") UUID registrationId);

    Optional<TuitionAccount> findByRegistrationId(UUID registrationId);

    List<TuitionAccount> findByRegistrationIdIn(Iterable<UUID> registrationIds);

    List<TuitionAccount> findByStudentId(UUID studentId);

    List<TuitionAccount> findByStudentIdAndSemesterId(
            UUID studentId,
            UUID semesterId
    );

    // All tuition accounts for a specific semester
    List<TuitionAccount> findBySemesterId(UUID semesterId);

    // All tuition accounts with balance > 0
// Used for outstanding balance report
    @Query("SELECT t FROM TuitionAccount t " +
            "WHERE t.balance > 0 " +
            "ORDER BY t.balance DESC")
    List<TuitionAccount> findAllWithOutstandingBalance();
}
