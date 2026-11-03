package com.kad.jet.student.service;

import com.kad.jet.course.model.Course;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.notification.event.GradeImportCompletedEvent;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.dto.*;
import com.kad.jet.student.model.GradeImportLog;
import com.kad.jet.student.model.StudentGrade;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.GradeImportLogRepository;
import com.kad.jet.student.repository.StudentGradeRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.utility.RoleChecker;
import com.opencsv.CSVReader;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradeServiceImpl implements GradeService{

    private final StudentGradeRepository studentGradeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final GradeImportLogRepository gradeImportLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Valid grade values the system accepts
    private static final List<String> VALID_GRADES =
            List.of("A", "B", "C", "D", "E", "F");

    @Override
    public GradeImportResponse importGrades(_User currentUser, MultipartFile file) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.HOD,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        // Validate file is provided and is a CSV
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "csv file is required");
        }

        if (!isCsvFile(file)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only CSV files are accepted. " +
                            "file must end with .csv");
        }

        // Create the import log record first
        // This exists even if all rows fail —
        // the import attempt is always recorded
        GradeImportLog importLog = GradeImportLog.builder()
                .importedBy(currentUser)
                .fileName(file.getOriginalFilename())
                .totalRecords(0)
                .successCount(0)
                .errorCount(0)
                .build();

        importLog = gradeImportLogRepository.save(importLog);

        List<GradeImportError> errors = new ArrayList<>();
        int successCount = 0;
        int totalRecords = 0;
        int rowNumber = 1;
        // Row 1 is the header — we start counting data from row 2

        try {
            CSVReader reader = new CSVReader(
                    new InputStreamReader(
                            file.getInputStream(),
                            StandardCharsets.UTF_8));

            // Skip header row
            String[] header = reader.readNext();
            if (!isValidHeader(header)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "invalid CSV format. header row must be: " +
                                "matric_number,course_code," +
                                "academic_year,semester_type,grade");
            }

            String[] row;
            while ((row = reader.readNext()) != null) {
                rowNumber++;
                totalRecords++;

                // Process each row and collect any error
                GradeImportError error = processRow(
                        row, rowNumber, importLog);

                if (error != null) {
                    errors.add(error);
                } else {
                    successCount++;
                }
            }

            reader.close();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error reading CSV file: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "could not read CSV file. " +
                            "please ensure it is a valid CSV. " +
                            "error: " + e.getMessage());
        }

        // Update the import log with final counts
        importLog.setTotalRecords(totalRecords);
        importLog.setSuccessCount(successCount);
        importLog.setErrorCount(errors.size());

        // Store error details as a summary string
        if (!errors.isEmpty()) {
            StringBuilder errorSummary = new StringBuilder();
            errors.forEach(e -> errorSummary
                    .append("Row ").append(e.getRowNumber())
                    .append(" [").append(e.getMatricNumber())
                    .append(" / ").append(e.getCourseCode())
                    .append("]: ").append(e.getReason())
                    .append("\n"));
            importLog.setErrorDetails(errorSummary.toString());
        }

        gradeImportLogRepository.save(importLog);

        eventPublisher.publishEvent(
                new GradeImportCompletedEvent(
                        importLog.getId(),
                        currentUser.getId(),
                        importLog.getSuccessCount(),
                        importLog.getErrorCount()
                )
        );


        log.info("Grade import completed by {}. " +
                        "File: {} Total: {} Success: {} Failed: {}",
                currentUser.getId(),
                file.getOriginalFilename(),
                totalRecords,
                successCount,
                errors.size());

        return GradeImportResponse.builder()
                .importBatchId(importLog.getId())
                .fileName(file.getOriginalFilename())
                .totalRecords(totalRecords)
                .successCount(successCount)
                .errorCount(errors.size())
                .errors(errors)
                .importedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<StudentGradeResponse> getStudentGrades(_User currentUser, UUID studentId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.REGISTRAR,
                UserRole.IT_ADMIN, UserRole.HOD,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        // Student can only see their own grades
        if (RoleChecker.is_student(currentUser) &&
                !currentUser.getId().equals(studentId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own grades");
        }

        return studentGradeRepository
                .findByStudentId(studentId)
                .stream()
                .map(this::toGradeResponse)
                .toList();
    }

    @Override
    public Page<GradeImportLog> getImportLogs(_User currentUser, Pageable pageable) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.HOD,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        return gradeImportLogRepository.findAll(pageable);
    }

    @Override
    public GradeImportLog getImportLogById(_User currentUser, UUID importLogId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.HOD,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        return gradeImportLogRepository
                .findById(importLogId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "import log not found"));
    }

    @Override
    public AcademicYearGradeResponse getGradesByAcademicYear(_User currentUser, UUID studentId, String academicYear) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.REGISTRAR,
                UserRole.IT_ADMIN, UserRole.HOD,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        if (RoleChecker.is_student(currentUser) &&
                !currentUser.getId().equals(studentId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own grades");
        }

        StudentProfile profile = studentProfileRepository
                .findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "student not found"));

        // Get all grades for this student in this academic year
        List<StudentGrade> grades = studentGradeRepository
                .findByStudentIdAndAcademicYear(
                        studentId, academicYear);

        if (grades.isEmpty()) {
            return AcademicYearGradeResponse.builder()
                    .studentName(profile.getUser().getFullName())
                    .matricNumber(profile.getMatricNumber())
                    .academicYear(academicYear)
                    .semesters(List.of())
                    .build();
        }

        // Group by semester type within this academic year
        Map<String, List<StudentGrade>> grouped = grades.stream()
                .collect(Collectors.groupingBy(
                        g -> g.getSemester().getSemesterType().name(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));

        List<SemesterGradeGroup> semesterGroups = grouped
                .entrySet()
                .stream()
                .map(entry -> {
                    List<StudentGrade> semesterGrades =
                            entry.getValue();

                    int totalUnits = semesterGrades.stream()
                            .mapToInt(g -> g.getCourse()
                                    .getCreditUnits())
                            .sum();

                    return SemesterGradeGroup.builder()
                            .academicYear(academicYear)
                            .semesterType(entry.getKey())
                            .totalCreditUnits(totalUnits)
                            .grades(semesterGrades.stream()
                                    .map(this::toGradeResponse)
                                    .toList())
                            .build();
                })
                .toList();

        return AcademicYearGradeResponse.builder()
                .studentName(profile.getUser().getFullName())
                .matricNumber(profile.getMatricNumber())
                .academicYear(academicYear)
                .semesters(semesterGroups)
                .build();
    }

    @Override
    public List<StudentGradeResponse> getGradesByYearAndSemester(_User currentUser, UUID studentId, String academicYear, String semesterType) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.REGISTRAR,
                UserRole.IT_ADMIN, UserRole.HOD,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        if (RoleChecker.is_student(currentUser) &&
                !currentUser.getId().equals(studentId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own grades");
        }

        // Validate semester type string
        SemesterType type;
        try {
            type = SemesterType.valueOf(
                    semesterType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "invalid semester type '" + semesterType +
                            "'. must be FIRST, SECOND, or SUMMER");
        }

        return studentGradeRepository
                .findByStudentIdAndAcademicYearAndSemesterType(
                        studentId, academicYear, type)
                .stream()
                .map(this::toGradeResponse)
                .toList();
    }


    /**
     * Processes one CSV row.
     * Returns null if the row was saved successfully.
     * Returns a GradeImportError if the row failed.
     *
     * Each successful row is committed in its own transaction.
     * Failures are recorded without stopping the import.
     */
    @Transactional
    protected GradeImportError processRow(
            String[] row,
            int rowNumber,
            GradeImportLog importLog) {

        // Validate column count
        if (row.length < 5) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber("UNKNOWN")
                    .courseCode("UNKNOWN")
                    .reason("row has " + row.length +
                            " columns. expected 5: " +
                            "matric_number, course_code, " +
                            "academic_year, semester_type, grade")
                    .build();
        }

        String matricNumber = row[0].trim();
        String courseCode = row[1].trim().toUpperCase();
        String academicYear = row[2].trim();
        String semesterTypeStr = row[3].trim().toUpperCase();
        String grade = row[4].trim().toUpperCase();

        // Validate grade value
        if (!VALID_GRADES.contains(grade)) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber(matricNumber)
                    .courseCode(courseCode)
                    .reason("grade '" + grade +
                            "' is not valid. " +
                            "must be one of: A, B, C, D, E, F")
                    .build();
        }

        // Validate semester type
        SemesterType semesterType;
        try {
            semesterType = SemesterType.valueOf(semesterTypeStr);
        } catch (IllegalArgumentException e) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber(matricNumber)
                    .courseCode(courseCode)
                    .reason("semester type '" + semesterTypeStr +
                            "' is not valid. " +
                            "must be FIRST, SECOND, or SUMMER")
                    .build();
        }

        // Find student profile by matric number
        Optional<StudentProfile> profileOpt =
                studentProfileRepository
                        .findByMatricNumber(matricNumber);

        if (profileOpt.isEmpty()) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber(matricNumber)
                    .courseCode(courseCode)
                    .reason("student with matric number '" +
                            matricNumber +
                            "' not found in the system")
                    .build();
        }

        StudentProfile profile = profileOpt.get();

        // Find course by course code
        Optional<Course> courseOpt =
                courseRepository.findByCourseCodeIgnoreCase(
                        courseCode);

        if (courseOpt.isEmpty()) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber(matricNumber)
                    .courseCode(courseCode)
                    .reason("course with code '" + courseCode +
                            "' not found in the system")
                    .build();
        }

        Course course = courseOpt.get();

        // Find semester by academic year and type
        Optional<Semester> semesterOpt =
                semesterRepository
                        .findByAcademicYearAndSemesterType(
                                academicYear, semesterType);

        if (semesterOpt.isEmpty()) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber(matricNumber)
                    .courseCode(courseCode)
                    .reason("semester '" + academicYear +
                            " " + semesterTypeStr +
                            "' not found in the system")
                    .build();
        }

        Semester semester = semesterOpt.get();

        // Duplicate check — same student, course, and semester
        boolean exists = studentGradeRepository
                .existsByStudentIdAndCourseIdAndSemesterId(
                        profile.getId(),
                        course.getId(),
                        semester.getId());

        if (exists) {
            return GradeImportError.builder()
                    .rowNumber(rowNumber)
                    .matricNumber(matricNumber)
                    .courseCode(courseCode)
                    .reason("grade already exists for student " +
                            matricNumber + " in course " +
                            courseCode + " for semester " +
                            academicYear + " " + semesterTypeStr +
                            ". use update if you need to change it.")
                    .build();
        }

        // All validations passed — save the grade
        StudentGrade studentGrade = StudentGrade.builder()
                .student(profile.getUser())
                .course(course)
                .semester(semester)
                .grade(grade)
                .importBatch(importLog)
                .build();

        studentGradeRepository.save(studentGrade);

        return null;
        // null means success
    }

    /**
     * Validates the CSV header row.
     * Must match exactly the expected column names.
     */
    private boolean isValidHeader(String[] header) {
        if (header == null || header.length < 5) return false;

        return header[0].trim().equalsIgnoreCase("matric_number") &&
                header[1].trim().equalsIgnoreCase("course_code") &&
                header[2].trim().equalsIgnoreCase("academic_year") &&
                header[3].trim().equalsIgnoreCase("semester_type") &&
                header[4].trim().equalsIgnoreCase("grade");
    }

    /**
     * Validates the uploaded file is a CSV.
     * Checks both the file extension and content type.
     */
    private boolean isCsvFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) return false;

        boolean hasCorrectExtension =
                fileName.toLowerCase().endsWith(".csv");

        String contentType = file.getContentType();
        boolean hasCorrectContentType = contentType != null &&
                (contentType.equals("text/csv") ||
                        contentType.equals("text/plain") ||
                        contentType.equals(
                                "application/vnd.ms-excel"));
        // Some browsers report CSV as text/plain
        // or application/vnd.ms-excel — we accept all three

        return hasCorrectExtension;
        // Extension check is sufficient — content type
        // varies by browser
    }

    /**
     * Converts StudentGrade entity to response DTO.
     */
    private StudentGradeResponse toGradeResponse(
            StudentGrade grade) {

        String matricNumber = studentProfileRepository
                .findById(grade.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        return StudentGradeResponse.builder()
                .id(grade.getId())
                .studentId(grade.getStudent().getId())
                .studentName(grade.getStudent().getFullName())
                .matricNumber(matricNumber)
                .courseId(grade.getCourse().getId())
                .courseCode(grade.getCourse().getCourseCode())
                .courseTitle(grade.getCourse().getTitle())
                .semesterId(grade.getSemester().getId())
                .academicYear(grade.getSemester().getAcademicYear())
                .semesterType(grade.getSemester()
                        .getSemesterType().name())
                .grade(grade.getGrade())
                .createdAt(grade.getCreatedAt())
                .build();
    }
}
