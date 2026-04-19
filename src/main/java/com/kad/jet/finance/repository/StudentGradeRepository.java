package com.kad.jet.finance.repository;

import com.kad.jet.student.model.StudentGrade;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
