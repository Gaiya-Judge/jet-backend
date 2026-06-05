package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.TuitionAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TuitionAccountRepository extends JpaRepository<TuitionAccount, UUID> {

    Optional<TuitionAccount> findByRegistrationId(UUID registrationId);

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
