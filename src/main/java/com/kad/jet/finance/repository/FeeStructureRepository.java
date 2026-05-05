package com.kad.jet.finance.repository;

import com.kad.jet.finance.model.FeeStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {

    boolean existsBySemesterIdAndProgramId(UUID semesterId, UUID programId);

    List<FeeStructure> findBySemesterId(UUID semesterId);

    Optional<FeeStructure> findBySemesterIdAndProgramId(UUID semesterId, UUID programId);

    Page<FeeStructure> findAll(Pageable pageable);
}
