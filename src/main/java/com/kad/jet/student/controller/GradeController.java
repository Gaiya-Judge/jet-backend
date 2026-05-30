package com.kad.jet.student.controller;

import com.kad.jet.student.dto.AcademicYearGradeResponse;
import com.kad.jet.student.dto.GradeImportResponse;
import com.kad.jet.student.dto.StudentGradeResponse;
import com.kad.jet.student.model.GradeImportLog;
import com.kad.jet.student.service.GradeService;
import com.kad.jet.user.model._User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/grades")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService gradeService;

    /**
     * IT_ADMIN or HOD uploads a CSV file containing grades.
     * Uses multipart/form-data — not JSON.
     * Frontend must send the file as a form field named "file".
     */
    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<?> importGrades(
            @AuthenticationPrincipal _User currentUser,
            @RequestParam("file") MultipartFile file) {

        GradeImportResponse response =
                gradeService.importGrades(currentUser, file);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns all grades for a specific student.
     * Student can only see their own grades.
     * Staff can see any student's grades.
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getStudentGrades(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID studentId) {

        List<StudentGradeResponse> response =
                gradeService.getStudentGrades(currentUser, studentId);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns paginated list of all CSV import batches.
     * Used by IT_ADMIN to see import history.
     */
    @GetMapping("/import-logs")
    public ResponseEntity<?> getImportLogs(
            @AuthenticationPrincipal _User currentUser,
            @PageableDefault(size = 10, sort = "importedAt",
                    direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<GradeImportLog> response =
                gradeService.getImportLogs(currentUser, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Returns one specific import batch log.
     * Shows how many rows succeeded and failed,
     * and the details of every failed row.
     */
    @GetMapping("/import-logs/{id}")
    public ResponseEntity<?> getImportLogById(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id) {

        GradeImportLog response =
                gradeService.getImportLogById(currentUser, id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/{studentId}/history")
    public ResponseEntity<?> getGradesByAcademicYear(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID studentId,
            @RequestParam String academicYear) {

        AcademicYearGradeResponse response =
                gradeService.getGradesByAcademicYear(
                        currentUser, studentId, academicYear);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/student/{studentId}/semester-history")
    public ResponseEntity<?> getGradesByYearAndSemester(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID studentId,
            @RequestParam String academicYear,
            @RequestParam String semesterType) {

        List<StudentGradeResponse> response =
                gradeService.getGradesByYearAndSemester(
                        currentUser,
                        studentId,
                        academicYear,
                        semesterType);

        return ResponseEntity.ok(response);
    }
}
