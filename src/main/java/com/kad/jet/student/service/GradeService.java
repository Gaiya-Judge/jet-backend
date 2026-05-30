package com.kad.jet.student.service;

import com.kad.jet.student.dto.AcademicYearGradeResponse;
import com.kad.jet.student.dto.GradeImportResponse;
import com.kad.jet.student.dto.StudentGradeResponse;
import com.kad.jet.student.model.GradeImportLog;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface GradeService {

    /**
     * Imports grades from a CSV file.
     * Processes each row independently — one bad row
     * does not stop the rest from being imported.
     */
    GradeImportResponse importGrades(
            _User currentUser, MultipartFile file);

    /**
     * Returns all grades for a specific student.
     */
    List<StudentGradeResponse> getStudentGrades(
            _User currentUser, UUID studentId);

    /**
     * Returns paginated list of all import batch logs.
     */
    Page<GradeImportLog> getImportLogs(
            _User currentUser, Pageable pageable);

    /**
     * Returns one specific import batch log by id.
     */
    GradeImportLog getImportLogById(
            _User currentUser, UUID importLogId);

    // All grades for a student in a specific academic year
    AcademicYearGradeResponse getGradesByAcademicYear(
            _User currentUser,
            UUID studentId,
            String academicYear);

    // Grades for a specific semester of a specific year
    List<StudentGradeResponse> getGradesByYearAndSemester(
            _User currentUser,
            UUID studentId,
            String academicYear,
            String semesterType);
}
