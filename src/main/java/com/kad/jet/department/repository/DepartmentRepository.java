package com.kad.jet.department.repository;

import com.kad.jet.department.model.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByProgramIdAndIsActiveTrue(UUID programId);

    Optional<Department> findByIdAndIsActiveTrue(UUID departmentId);

    List<Department> findByProgramId(UUID programId);
    Page<Department> findByIsActiveTrue(Pageable pageable);

    boolean existsByNameIgnoreCase(String departmentName);
}
