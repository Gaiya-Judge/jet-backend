package com.kad.jet.program.repository;

import com.kad.jet.program.model.Program;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    List<Program> findByIsActiveTrue();

    Optional<Program> findByIdAndIsActive(UUID programId, boolean isActive);

    Page<Program> findByIsActiveTrue(Pageable pageable);

    boolean existsByName(String name);

    boolean existsById(UUID id);
}
