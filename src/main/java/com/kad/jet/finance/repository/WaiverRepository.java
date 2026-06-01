package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.Waiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaiverRepository extends JpaRepository<Waiver, UUID> {

    // One waiver per tuition account
    Optional<Waiver> findByTuitionAccountId(UUID tuitionAccountId);

    boolean existsByTuitionAccountId(UUID tuitionAccountId);

    List<Waiver> findAllByOrderByApprovedAtDesc();
}
