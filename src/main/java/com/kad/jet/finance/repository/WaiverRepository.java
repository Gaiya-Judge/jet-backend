package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.Waiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WaiverRepository extends JpaRepository<Waiver, UUID> {

    // All waivers for a tuition account
    // A tuition account can have multiple waivers
    // e.g. scholarship + hardship waiver
    List<Waiver> findByTuitionAccountId(UUID tuitionAccountId);

    // All waivers — for the waiver report
// Ordered by most recent first
    List<Waiver> findAllByOrderByApprovedAtDesc();

    // Waivers for a specific semester
    @Query("SELECT w FROM Waiver w " +
            "WHERE w.tuitionAccount.semester.id = :semesterId")
    List<Waiver> findBySemesterId(
            @Param("semesterId") UUID semesterId);
}
