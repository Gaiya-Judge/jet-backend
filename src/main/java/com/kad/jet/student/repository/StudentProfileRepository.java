package com.kad.jet.student.repository;

import com.kad.jet.student.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByMatricNumber(String matricNumber);

    @Query(value = "select profile from StudentProfile profile join fetch profile.user " +
            "join fetch profile.department join fetch profile.program " +
            "where :search is null or " +
            "lower(profile.matricNumber) like lower(concat('%', :search, '%')) or " +
            "lower(profile.user.fullName) like lower(concat('%', :search, '%')) or " +
            "lower(profile.user.email) like lower(concat('%', :search, '%'))",
            countQuery = "select count(profile) from StudentProfile profile " +
                    "where :search is null or " +
                    "lower(profile.matricNumber) like lower(concat('%', :search, '%')) or " +
                    "lower(profile.user.fullName) like lower(concat('%', :search, '%')) or " +
                    "lower(profile.user.email) like lower(concat('%', :search, '%'))")
    Page<StudentProfile> searchDirectory(@Param("search") String search, Pageable pageable);
}
