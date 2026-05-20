package com.kad.jet.semester.repository;

import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.model.SemesterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    Optional<Semester> findByStatus(SemesterStatus status);

    boolean existsByAcademicYearAndSemesterType(
            String academicYear,
            SemesterType semesterType
    );

    Page<Semester> findAll(Pageable pageable);

    Optional<Semester> findByAcademicYearAndSemesterType(
            String academicYear, SemesterType semesterType);
}
