package com.kad.jet.program.repository;

import com.kad.jet.program.model.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    List<Program> findByIsActiveTrue();

    boolean existsByName(String name);
}
