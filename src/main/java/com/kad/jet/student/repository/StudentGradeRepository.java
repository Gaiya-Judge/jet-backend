package com.kad.jet.student.repository;

import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.student.model.StudentGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentGradeRepository extends JpaRepository<StudentGrade, UUID> {

    List<StudentGrade> findByStudentId(UUID studentId);

    Optional<StudentGrade> findByStudentIdAndCourseId(
            UUID studentId,
            UUID courseId
    );

    List<StudentGrade> findByStudentIdAndSemesterId(
            UUID studentId,
            UUID semesterId
    );

    // Check if a grade already exists for this exact combination
    // Used for duplicate detection during import
    boolean existsByStudentIdAndCourseIdAndSemesterId(
            UUID studentId, UUID courseId, UUID semesterId);

    // Grades for a student in a specific academic year
// across all semesters of that year
    @Query("SELECT g FROM StudentGrade g " +
            "WHERE g.student.id = :studentId " +
            "AND g.semester.academicYear = :academicYear " +
            "ORDER BY g.semester.semesterType ASC")
    List<StudentGrade> findByStudentIdAndAcademicYear(
            @Param("studentId") UUID studentId,
            @Param("academicYear") String academicYear);

    // Grades for a specific academic year and semester type
    @Query("SELECT g FROM StudentGrade g " +
            "WHERE g.student.id = :studentId " +
            "AND g.semester.academicYear = :academicYear " +
            "AND g.semester.semesterType = :semesterType")
    List<StudentGrade> findByStudentIdAndAcademicYearAndSemesterType(
            @Param("studentId") UUID studentId,
            @Param("academicYear") String academicYear,
            @Param("semesterType") SemesterType semesterType);
}
