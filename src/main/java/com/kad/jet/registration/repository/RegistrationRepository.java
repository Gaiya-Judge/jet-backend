package com.kad.jet.registration.repository;

import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    Optional<Registration> findByStudentIdAndSemesterId(
            UUID studentId,
            UUID semesterId
    );

    List<Registration> findByStatus(RegistrationStatus status);

    List<Registration> findByStudentId(UUID studentId);

    boolean existsByStudentIdAndSemesterId(
            UUID studentId,
            UUID semesterId
    );
}
