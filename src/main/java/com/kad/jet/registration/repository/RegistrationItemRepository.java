package com.kad.jet.registration.repository;

import com.kad.jet.registration.model.RegistrationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationItemRepository extends JpaRepository<RegistrationItem, UUID> {

    List<RegistrationItem> findByRegistrationId(UUID registrationId);

    Optional<RegistrationItem> findByRegistrationIdAndCourseId(
            UUID registrationId, UUID courseId);

    boolean existsByRegistrationIdAndCourseId(
            UUID registrationId, UUID courseId);
}
