package com.kad.jet.department.repository;

import com.kad.jet.department.model.Department;
import jakarta.validation.groups.ConvertGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByProgramId(UUID programId);

    List<Department> findByIsActiveTrue();
}
