package com.kad.jet.registration.service;

import com.kad.jet.course.model.Course;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.StudentGradeRepository;
import com.kad.jet.finance.repository.TuitionRepository;
import com.kad.jet.registration.dto.*;
import com.kad.jet.registration.mapper.RegistrationMapper;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.CreditBalanceRepository;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.model.StudentGrade;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImp implements RegistrationService{

    private final RegistrationRepository registrationRepository;
    private final RegistrationItemRepository registrationItemRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TuitionRepository tuitionRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final AdminChargeRepository adminChargeRepository;
    private final CreditBalanceRepository creditBalanceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RegistrationResponse createDraft(_User currentUser, CreateRegistrationRequest request) {

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.STUDENT,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        boolean isAdmin = RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);

        _User student = resolveStudent(request, currentUser, isAdmin);

        System.out.println("before admin assist check");
        if (isAdmin && (request.getAssistReason() == null
                || request.getAssistReason().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "assist reason is required when registering " +
                            "on behalf of a student");
        }

        System.out.println("before semester active check");
        Semester activeSemester = semesterRepository
                .findByStatus(SemesterStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "no active semester. registration is closed"));

        System.out.println("checking if student id and semester id already exits");
        if (registrationRepository.existsByStudentIdAndSemesterId(
                student.getId(), activeSemester.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "student already has a registration " +
                            "for this semester");
        }

        System.out.println("before checking out standing balance");
        checkOutstandingBalance(student.getId());

        System.out.println("before persisting data into db");
        Registration registration = Registration.builder()
                .student(student)
                .semester(activeSemester)
                .status(RegistrationStatus.DRAFT)
                .totalCreditUnit(0)
                .assistedBy(isAdmin ? currentUser : null)
                .assistReason(isAdmin
                        ? request.getAssistReason() : null)
                .build();

        registration = registrationRepository.save(registration);

        System.out.println("before carry over check");
        autoAddCarryoverCourses(registration, student);

        registration = findById(registration.getId());

        System.out.println("before return response");
        return buildResponse(registration);
    }

    @Override
    public RegistrationResponse getRegistrationById(_User currentUser, UUID id) {
        return null;
    }

    @Override
    public Page<RegistrationResponse> getAllRegistrations(_User currentUser, Pageable pageable) {
        return null;
    }

    @Override
    public Page<RegistrationResponse> getPendingRegistrations(_User currentUser, Pageable pageable) {
        return null;
    }

    @Override
    public List<RegistrationResponse> getMyRegistrations(_User currentUser) {
        return List.of();
    }

    @Override
    @Transactional
    public RegistrationResponse addCourse(_User currentUser, UUID registrationId, AddCourseRequest request) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        boolean isAdmin = RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);


        if (isAdmin && (request.getAssistReason() == null
                || request.getAssistReason().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "assist reason is required");
        }

        Registration registration = findById(registrationId);

        if (registration.getStatus() != RegistrationStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only DRAFT registrations can be modified. " +
                            "current status: " + registration.getStatus());
        }

        Course course = courseRepository
                .findById(request.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "course not found"));

        StudentProfile studentProfile = studentProfileRepository
                .findById(registration.getStudent().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "student profile not found"));

        if (registration.getSemester().getSemesterType() != SemesterType.SUMMER) {

            if (course.getSemesterType() != registration.getSemester().getSemesterType()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "course " + course.getCourseCode() +
                                " belongs to " + course.getSemesterType() +
                                " semester and cannot be added to a " +
                                registration.getSemester().getSemesterType() +
                                " semester registration");
            }
        }

        if (!course.getLevel().equals(studentProfile.getLevel())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "course level " + course.getLevel() +
                            " does not match student level " +
                            studentProfile.getLevel());
        }

        if (registrationItemRepository
                .existsByRegistrationIdAndCourseId(
                        registrationId, course.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "course already added to this registration");
        }

        RegistrationItem item = RegistrationItem.builder()
                .registration(registration)
                .course(course)
                .isCarryOver(false)
                .isDroppable(true)
                .build();

        registrationItemRepository.save(item);

        registration.setTotalCreditUnit(
                registration.getTotalCreditUnit() + course.getCreditUnits());

        registrationRepository.save(registration);

        return buildResponse(registration);

    }

    @Override
    @Transactional
    public RegistrationResponse removeCourse(_User currentUser, UUID registrationId, UUID courseId) {
        return null;
    }

    @Override
    public List<RegistrationItemResponse> getRegistrationCourses(_User currentUser, UUID registrationId) {
        return List.of();
    }

    @Override
    @Transactional
    public RegistrationResponse submitRegistration(_User currentUser, UUID registrationId, SubmitRegistrationRequest request) {
        return null;
    }

    @Override
    @Transactional
    public RegistrationResponse approveRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request) {
        return null;
    }

    @Override
    @Transactional
    public RegistrationResponse rejectRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request) {
        return null;
    }

    @Override
    public TuitionEstimateResponse getTuitionEstimate(_User currentUser, UUID registrationId) {
        return null;
    }


    private _User resolveStudent(CreateRegistrationRequest request, _User currentUser, boolean isAdmin) {

        if (isAdmin) {
            if (request.getStudentId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Student id is required when creating " +
                                "registration on behalf of a student");
            }
            return userRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Student not found"));
        }

        return currentUser;
    }

    private boolean isItAdmin(_User user) {
        return user.getUserRole() == UserRole.IT_ADMIN ||
                user.getUserRole() == UserRole.SUPER_ADMIN;
    }

    private Registration findById(UUID id) {
        return registrationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Registration not found"));
    }

    private void checkOutstandingBalance(UUID studentId) {
        boolean hasBalance = tuitionRepository
                .findByStudentId(studentId)
                .stream()
                .anyMatch(account -> account.getBalance()
                        .compareTo(BigDecimal.ZERO) > 0);

        if (hasBalance) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You have an outstanding tuition balance " +
                            "from a previous semester. " +
                            "Please clear your balance before registering.");
        }
    }

    private void autoAddCarryoverCourses(Registration registration, _User student) {

        List<String> failingGrades = List.of("D", "E", "F");

        List<StudentGrade> failedGrades = studentGradeRepository
                .findByStudentId(student.getId())
                .stream()
                .filter(g -> failingGrades.contains(g.getGrade()))
                .toList();

        if (failedGrades.isEmpty()) return;

        Set<UUID> passedCourseIds = studentGradeRepository
                .findByStudentId(student.getId())
                .stream()
                .filter(g -> !failingGrades.contains(g.getGrade()))
                .map(g -> g.getCourse().getId())
                .collect(Collectors.toSet());

        int totalCarryoverUnits = 0;

        for (StudentGrade failedGrade : failedGrades) {
            UUID courseId = failedGrade.getCourse().getId();

            if (passedCourseIds.contains(courseId)) continue;

            if (registrationItemRepository
                    .existsByRegistrationIdAndCourseId(
                            registration.getId(), courseId)) continue;

            RegistrationItem item = RegistrationItem.builder()
                    .registration(registration)
                    .course(failedGrade.getCourse())
                    .isCarryOver(true)
                    .isDroppable(false)
                    .build();

            registrationItemRepository.save(item);
            totalCarryoverUnits += failedGrade
                    .getCourse().getCreditUnits();
        }

        registration.setTotalCreditUnit(
                registration.getTotalCreditUnit()
                        + totalCarryoverUnits);
        registrationRepository.save(registration);
    }

    private RegistrationItemResponse toItemResponse(
            RegistrationItem item) {
        return RegistrationItemResponse.builder()
                .id(item.getId())
                .courseId(item.getCourse().getId())
                .courseCode(item.getCourse().getCourseCode())
                .courseTitle(item.getCourse().getTitle())
                .creditUnits(item.getCourse().getCreditUnits())
                .courseType(item.getCourse().getCourseType().name())
                .isCarryover(item.getIsCarryOver())
                .isDroppable(item.getIsDroppable())
                .build();
    }

    private RegistrationResponse buildResponse(
            Registration registration) {

        List<RegistrationItemResponse> courses =
                registrationItemRepository
                        .findByRegistrationId(registration.getId())
                        .stream()
                        .map(RegistrationMapper::toItemDto)
                        .toList();

        String matricNumber = studentProfileRepository
                .findById(registration.getStudent().getId())
                .map(StudentProfile::getMatricNumber)
                .orElse("N/A");

        return RegistrationMapper.toDto(
                registration, matricNumber, courses);
    }
}
