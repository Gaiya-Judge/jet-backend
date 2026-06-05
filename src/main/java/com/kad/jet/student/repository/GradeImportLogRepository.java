package com.kad.jet.student.repository;

import com.kad.jet.student.model.GradeImportLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GradeImportLogRepository extends JpaRepository<GradeImportLog, UUID> {

    Page<GradeImportLog> findAll(Pageable pageable);
}
