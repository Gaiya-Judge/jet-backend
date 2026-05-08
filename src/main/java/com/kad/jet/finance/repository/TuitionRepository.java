package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.TuitionAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TuitionRepository extends JpaRepository<TuitionAccount, UUID> {

    Optional<TuitionAccount> findByRegistrationId(UUID registrationId);

    List<TuitionAccount> findByStudentId(UUID studentId);

    List<TuitionAccount> findByStudentIdAndSemesterId(
            UUID studentId,
            UUID semesterId
    );
}
