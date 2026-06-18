package com.kad.jet.registration.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.course.model.Course;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.finance.model.*;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.student.repository.StudentGradeRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.registration.dto.*;
import com.kad.jet.registration.mapper.RegistrationMapper;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.finance.repository.CreditBalanceRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationServiceImp implements RegistrationService{

    private final RegistrationRepository registrationRepository;
    private final RegistrationItemRepository registrationItemRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TuitionAccountRepository tuitionRepository;
    private final FeeStructureRepository feeStructureRepository;
    private final AdminChargeRepository adminChargeRepository;
    private final CreditBalanceRepository creditBalanceRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional
    public RegistrationResponse createDraft(_User currentUser, CreateRegistrationRequest request) {

        if (request == null) {
            request = new CreateRegistrationRequest();
        }

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.STUDENT,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        boolean isAdmin = RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);

        _User student = resolveStudent(request, currentUser, isAdmin);

        if (isAdmin && (request.getAssistReason() == null
                || request.getAssistReason().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "assist reason is required when registering " +
                            "on behalf of a student");
        }

        Semester activeSemester = semesterRepository
                .findByStatus(SemesterStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "no active semester. registration is closed"));

        if (registrationRepository.existsByStudentIdAndSemesterId(
                student.getId(), activeSemester.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "student already has a registration " +
                            "for this semester");
        }

        checkOutstandingBalance(student.getId());

        Registration registration = Registration.builder()
                .student(student)
                .semester(activeSemester)
                .status(RegistrationStatus.DRAFT)
                .totalCreditUnits(0)
                .assistedBy(isAdmin ? currentUser : null)
                .assistReason(isAdmin
                        ? request.getAssistReason() : null)
                .build();

        registration = registrationRepository.save(registration);

        autoAddCarryoverCourses(registration, student);

        registration = findById(registration.getId());

        return buildResponse(registration);
    }

    @Override
    public RegistrationResponse getRegistrationById(_User currentUser, UUID id) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.DEAN,
                UserRole.REGISTRAR, UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN, UserRole.BURSAR)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Registration registration = findById(id);

        ensureStudentOwnsRegistration(currentUser, registration);

        return buildResponse(registration);
    }

    @Override
    public Page<RegistrationResponse> getAllRegistrations(_User currentUser, Pageable pageable) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.DEAN, UserRole.REGISTRAR,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN, UserRole.BURSAR)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        return registrationRepository.findAll(pageable)
                .map(this::buildResponse);
    }

    @Override
    public Page<RegistrationResponse> getPendingRegistrations(_User currentUser, Pageable pageable) {

        if (!RoleChecker.is_dean(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        return registrationRepository
                .findByStatus(
                        RegistrationStatus.PENDING_DEAN, pageable)
                .map(this::buildResponse);
    }

    @Override
    public List<RegistrationResponse> getMyRegistrations(_User currentUser) {

        if (!RoleChecker.is_student(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        return registrationRepository
                .findByStudentId(currentUser.getId())
                .stream()
                .map(this::buildResponse)
                .toList();
    }

    @Override
    @Transactional
    public RegistrationResponse addCourse(_User currentUser, UUID registrationId, AddCourseRequest request) {

        if (request == null){
            request =  new AddCourseRequest();
        }

        if (!RoleChecker.hasAnyRole(currentUser, UserRole.STUDENT, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        boolean isAdmin = RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);

        if (isAdmin && (request.getAssistReason() == null || request.getAssistReason().isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "assist reason is required");
        }

        Registration registration = findById(registrationId);

        ensureStudentOwnsRegistration(currentUser, registration);

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

        // Run all course validations
        validateCourseForRegistration(course, studentProfile, registration, registrationId,false);

        // Add the course
        RegistrationItem item = RegistrationItem.builder()
                .registration(registration)
                .course(course)
                .isCarryOver(false)
                .isDroppable(true)
                .build();

        registrationItemRepository.save(item);

        registration.setTotalCreditUnits(
                registration.getTotalCreditUnits()
                        + course.getCreditUnits());

        return buildResponse(registrationRepository.save(registration));
    }


    @Override
    @Transactional
    public RegistrationResponse removeCourse(_User currentUser, UUID registrationId, UUID courseId) {

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Registration registration = findById(registrationId);

        ensureStudentOwnsRegistration(currentUser, registration);

        if (registration.getStatus() != RegistrationStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only DRAFT registrations can be modified");
        }

        RegistrationItem item = registrationItemRepository
                .findByRegistrationIdAndCourseId(
                        registrationId, courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "course not found in this registration"));

        // Carryover courses cannot be removed
        if (!item.getIsDroppable()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "carryover courses cannot be removed " +
                            "from registration");
        }

        // Subtract credit units before deleting
        registration.setTotalCreditUnits(
                registration.getTotalCreditUnits()
                        - item.getCourse().getCreditUnits());

        registrationItemRepository.delete(item);

        return buildResponse(registrationRepository.save(registration));
    }

    // ... existing code ...
    @Override
    public List<RegistrationItemResponse> getRegistrationCourses(_User currentUser, UUID registrationId) {
        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.DEAN,
                UserRole.REGISTRAR, UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Registration registration = findById(registrationId);

       ensureStudentOwnsRegistration(currentUser, registration);

        return registrationItemRepository
                .findByRegistrationId(registrationId)
                .stream()
                .map(RegistrationMapper::toItemDto)
                .toList();
    }

    @Override
    @Transactional
    public RegistrationResponse submitRegistration(_User currentUser, UUID registrationId, SubmitRegistrationRequest request) {

        if (request == null){
            request = new SubmitRegistrationRequest();
        }

        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        boolean isAdmin = RoleChecker.hasAnyRole(currentUser,
                UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);

        Registration registration = findById(registrationId);

        ensureStudentOwnsRegistration(currentUser, registration);

        if (registration.getStatus() != RegistrationStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only DRAFT registrations can be submitted");
        }

        // Validate credit unit limits before submitting
        validateCreditUnitLimits(registration.getTotalCreditUnits());

        if (isAdmin) {
            if (request.getAssistReason() == null
                    || request.getAssistReason().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "assist reason is required");
            }
            registration.setAssistedBy(currentUser);
            registration.setAssistReason(request.getAssistReason());
        }

        registration.setStatus(RegistrationStatus.PENDING_DEAN);
        registration.setSubmittedAt(LocalDateTime.now());

        return buildResponse(registrationRepository.save(registration));
    }

    @Override
    @Transactional
    public RegistrationResponse approveRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request) {

        if (!RoleChecker.is_dean(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Registration registration = findById(registrationId);

        if (registration.getStatus() !=
                RegistrationStatus.PENDING_DEAN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only PENDING_DEAN registrations can be approved");
        }

        StudentProfile studentProfile = getStudentProfile(
                registration.getStudent().getId());

        feeStructureRepository.findBySemesterIdAndProgramId(
                        registration.getSemester().getId(),
                        studentProfile.getProgram().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot approve: fee structure is not configured for "
                                + studentProfile.getProgram().getName()
                                + " this semester. Ask the Bursar to set fees first."));

        registration.setStatus(RegistrationStatus.APPROVED);
        registration.setDeanReviewAt(LocalDateTime.now());

        if (request != null && request.getRemarks() != null){
            registration.setDeanRemark(request.getRemarks());
        }

        registrationRepository.save(registration);

        // Auto create tuition account in same transaction
        // If this fails the approval rolls back
        createTuitionAccount(registration);

        return buildResponse(registration);
    }

    @Override
    @Transactional
    public RegistrationResponse rejectRegistration(_User currentUser, UUID registrationId, DeanReviewRequest request) {
        if (!RoleChecker.is_dean(currentUser)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Registration registration = findById(registrationId);

        if (registration.getStatus() !=
                RegistrationStatus.PENDING_DEAN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only PENDING_DEAN registrations can be rejected");
        }

        // Remarks are mandatory on rejection
        if (request.getRemarks() == null
                || request.getRemarks().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "remarks are required when rejecting " +
                            "a registration");
        }

        // Status returns to DRAFT — same record
        // Student edits and resubmits
        registration.setStatus(RegistrationStatus.DRAFT);
        registration.setDeanReviewAt(LocalDateTime.now());
        registration.setDeanRemark(request.getRemarks());

        return buildResponse(registrationRepository.save(registration));
    }

    @Override
    public TuitionEstimateResponse getTuitionEstimate(_User currentUser, UUID registrationId) {
        if (!RoleChecker.hasAnyRole(currentUser,
                UserRole.STUDENT, UserRole.IT_ADMIN,
                UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "unauthorized request");
        }

        Registration registration = findById(registrationId);
        Semester semester = registration.getSemester();

        StudentProfile studentProfile = getStudentProfile(
                registration.getStudent().getId());

        FeeStructure feeStructure = feeStructureRepository
                .findBySemesterIdAndProgramId(
                        semester.getId(),
                        studentProfile.getProgram().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "fee structure not configured " +
                                "for this semester and program. " +
                                "please contact the bursar"));

        BigDecimal tuitionAmount = feeStructure
                .getCostPerCreditUnit()
                .multiply(BigDecimal.valueOf(
                        registration.getTotalCreditUnits()));

        List<AdminCharge> charges = adminChargeRepository
                .findBySemesterId(semester.getId());

        BigDecimal adminChargesTotal = charges.stream()
                .map(AdminCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> breakdown = charges.stream()
                .collect(Collectors.toMap(
                        AdminCharge::getName,
                        AdminCharge::getAmount));

        BigDecimal creditCarriedForward = creditBalanceRepository
                .findByStudentIdAndIsAppliedFalse(
                        registration.getStudent().getId())
                .stream()
                .map(CreditBalance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal estimatedTotal = tuitionAmount
                .add(adminChargesTotal)
                .subtract(creditCarriedForward);

        if (estimatedTotal.compareTo(BigDecimal.ZERO) < 0) {
            estimatedTotal = BigDecimal.ZERO;
        }

        int minPaymentPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        BigDecimal minimumPayment = estimatedTotal
                .multiply(BigDecimal.valueOf(minPaymentPercentage))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);

        return TuitionEstimateResponse.builder()
                .totalCreditUnits(registration.getTotalCreditUnits())
                .costPerCreditUnit(feeStructure.getCostPerCreditUnit())
                .tuitionAmount(tuitionAmount)
                .adminChargesTotal(adminChargesTotal)
                .adminChargesBreakdown(breakdown)
                .creditCarriedForward(creditCarriedForward)
                .estimatedTotal(estimatedTotal)
                .minimumPayment(minimumPayment)
                .build();
    }

    @Override
    @Transactional
    public BulkAddCourseResponse addCourses(_User currentUser, UUID registrationId, BulkAddCourseRequest request) {

        if (request == null){
            request = new BulkAddCourseRequest();
        }
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

        ensureStudentOwnsRegistration(currentUser, registration);

        // Only DRAFT registrations can be modified
        if (registration.getStatus() != RegistrationStatus.DRAFT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "only DRAFT registrations can be modified. " +
                            "current status: " + registration.getStatus());
        }

        // Get student profile once — reused for all course checks
        StudentProfile studentProfile = getStudentProfile(
                registration.getStudent().getId());

        List<UUID> added = new ArrayList<>();
        List<BulkAddCourseResponse.FailedCourse> failed =
                new ArrayList<>();

        int totalUnitsAddedThisCall = 0;

        for (UUID courseId : request.getCourseIds()) {

            try {
                Course course = courseRepository
                        .findById(courseId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "course not found"));

                // Level check
                if (!course.getLevel()
                        .equals(studentProfile.getLevel())) {
                    failed.add(buildFailedCourse(courseId,
                            course.getCourseCode(),
                            "course level " + course.getLevel() +
                                    " does not match student level " +
                                    studentProfile.getLevel()));
                    continue;
                }

                // Semester type check
                // Skip for SUMMER — only carryovers run in summer
                if (registration.getSemester().getSemesterType()
                        != SemesterType.SUMMER) {
                    if (course.getSemesterType() !=
                            registration.getSemester()
                                    .getSemesterType()) {
                        failed.add(buildFailedCourse(courseId,
                                course.getCourseCode(),
                                "course " + course.getCourseCode() +
                                        " belongs to " +
                                        course.getSemesterType() +
                                        " semester. current semester is " +
                                        registration.getSemester()
                                                .getSemesterType()));
                        continue;
                    }
                }

                // Department restriction check
                if (course.getIsDepartmentRestricted()) {
                    UUID studentDeptId = studentProfile
                            .getDepartment().getId();
                    UUID courseDeptId = course
                            .getDepartment().getId();

                    if (!studentDeptId.equals(courseDeptId)) {
                        failed.add(buildFailedCourse(courseId,
                                course.getCourseCode(),
                                "course " + course.getCourseCode() +
                                        " is restricted to " +
                                        course.getDepartment().getName() +
                                        " department students only"));
                        continue;
                    }
                }

                // Duplicate check
                if (registrationItemRepository
                        .existsByRegistrationIdAndCourseId(
                                registrationId, courseId)) {
                    failed.add(buildFailedCourse(courseId,
                            course.getCourseCode(),
                            "course already added " +
                                    "to this registration"));
                    continue;
                }

                // Credit unit limit check
                // Considers units already in the registration
                // plus units added so far in this bulk call
                int projectedTotal =
                        registration.getTotalCreditUnits()
                                + totalUnitsAddedThisCall
                                + course.getCreditUnits();

                if (projectedTotal > getMaxCreditUnits()) {
                    failed.add(buildFailedCourse(courseId,
                            course.getCourseCode(),
                            "adding this course would exceed " +
                                    "the maximum of " +
                                    getMaxCreditUnits() +
                                    " credit units. " +
                                    "projected total: " + projectedTotal));
                    continue;
                }

                // All checks passed — add the course
                RegistrationItem item = RegistrationItem.builder()
                        .registration(registration)
                        .course(course)
                        .isCarryOver(false)
                        .isDroppable(true)
                        .build();

                registrationItemRepository.save(item);
                added.add(courseId);
                totalUnitsAddedThisCall += course.getCreditUnits();

            } catch (ResponseStatusException e) {
                failed.add(buildFailedCourse(
                        courseId, "UNKNOWN", e.getReason()));
            }
        }

        // Update total credit units once after all courses processed
        if (totalUnitsAddedThisCall > 0) {
            registration.setTotalCreditUnits(
                    registration.getTotalCreditUnits()
                            + totalUnitsAddedThisCall);
            registrationRepository.save(registration);
        }

        return BulkAddCourseResponse.builder()
                .added(added)
                .failed(failed)
                .totalCreditUnits(registration.getTotalCreditUnits())
                .registration(buildResponse(registration))
                .build();
    }

    @Override
    @Transactional
    public AddDropResponse addCourseDuringAddDrop(_User currentUser, UUID registrationId, AddDropCourseRequest request) {

        if (request == null){
            request = new AddDropCourseRequest();
        }
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

        ensureStudentOwnsRegistration(currentUser, registration);

        // Registration must be APPROVED or COMPLETED
        // Cannot add/drop on DRAFT or PENDING_DEAN
        if (registration.getStatus() != RegistrationStatus.APPROVED &&
                registration.getStatus() !=
                        RegistrationStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "add/drop is only allowed on APPROVED or " +
                            "COMPLETED registrations. current status: " +
                            registration.getStatus());
        }

        // Check add/drop window is open
        validateAddDropWindow(registration.getSemester());

        Course course = courseRepository
                .findById(request.getCourseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "course not found"));

        StudentProfile studentProfile = getStudentProfile(
                registration.getStudent().getId());

        // Run all course validations
        // false = not a carryover so all checks apply
        validateCourseForRegistration(
                course, studentProfile, registration,
                registrationId, false);

        // Get tuition account before changes
        TuitionAccount account = getTuitionAccount(registrationId);
        BigDecimal previousTotalDue = account.getTotalDue();

        // Add the course to registration
        RegistrationItem item = RegistrationItem.builder()
                .registration(registration)
                .course(course)
                .isCarryOver(false)
                .isDroppable(true)
                .build();

        registrationItemRepository.save(item);

        // Update total credit units
        registration.setTotalCreditUnits(
                registration.getTotalCreditUnits()
                        + course.getCreditUnits());
        registrationRepository.save(registration);

        // Recalculate tuition — adding units increases the bill
        BigDecimal additionalTuition = recalculateTuitionForAddDrop(
                account, registration, studentProfile);

        BigDecimal newTotalDue = account.getTotalDue();
        BigDecimal difference = newTotalDue.subtract(previousTotalDue);

        log.info("Course {} added during add/drop for " +
                        "registration {}. Additional charge: ₦{}",
                course.getCourseCode(),
                registrationId,
                difference);

        return AddDropResponse.builder()
                .registration(buildResponse(registration))
                .previousTotalDue(previousTotalDue)
                .newTotalDue(newTotalDue)
                .difference(difference)
                .message("course " + course.getCourseCode() +
                        " added successfully. " +
                        "additional payment of ₦" + difference +
                        " is required.")
                .build();
    }

    @Override
    @Transactional
    public AddDropResponse dropCourseDuringAddDrop(_User currentUser, UUID registrationId, UUID courseId, String assistReason) {

        if (assistReason == null){
            assistReason = new String();
        }

            if (!RoleChecker.hasAnyRole(currentUser,
                    UserRole.STUDENT, UserRole.IT_ADMIN,
                    UserRole.SUPER_ADMIN)) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "unauthorized request");
            }

            boolean isAdmin = RoleChecker.hasAnyRole(currentUser,
                    UserRole.IT_ADMIN, UserRole.SUPER_ADMIN);

            if (isAdmin && (assistReason == null
                    || assistReason.isBlank())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "assist reason is required");
            }

            Registration registration = findById(registrationId);

            ensureStudentOwnsRegistration(currentUser, registration);

            // Must be APPROVED or COMPLETED
            if (registration.getStatus() != RegistrationStatus.APPROVED &&
                    registration.getStatus() !=
                            RegistrationStatus.COMPLETED) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "add/drop is only allowed on APPROVED or " +
                                "COMPLETED registrations. current status: " +
                                registration.getStatus());
            }

            // Check window is open
            validateAddDropWindow(registration.getSemester());

            // Find the course in this registration
            RegistrationItem item = registrationItemRepository
                    .findByRegistrationIdAndCourseId(
                            registrationId, courseId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "course not found in this registration"));

            // Carryover courses cannot be dropped
            if (!item.getIsDroppable()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "carryover courses cannot be dropped");
            }

            // Credit unit minimum check
            int newTotalUnits = registration.getTotalCreditUnits()
                    - item.getCourse().getCreditUnits();

            int minUnits = systemConfigService.getIntValue(
                    SystemConfigKeys.MIN_CREDIT_UNITS);

            if (newTotalUnits < minUnits) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "dropping this course would bring your " +
                                "total credit units to " + newTotalUnits +
                                " which is below the minimum of " + minUnits);
            }

            // Get tuition account before changes
            TuitionAccount account = getTuitionAccount(registrationId);
            BigDecimal previousTotalDue = account.getTotalDue();

            String courseCode = item.getCourse().getCourseCode();
            int droppedUnits = item.getCourse().getCreditUnits();

            // Remove the course
            registrationItemRepository.delete(item);

            // Update credit units
            registration.setTotalCreditUnits(newTotalUnits);
            registrationRepository.save(registration);

            // Recalculate tuition account figures
            StudentProfile studentProfile = getStudentProfile(
                    registration.getStudent().getId());

            recalculateTuitionForAddDrop(
                    account, registration, studentProfile);

        //refresh to avoid stale balance
        account = getTuitionAccount(registrationId);

        BigDecimal newTotalDue = account.getTotalDue();


        // Credit is based on what student actually PAID
        // vs what they now OWE after the drop
        // Not just the difference in totalDue
        BigDecimal creditToGenerate = BigDecimal.ZERO;

        BigDecimal refundAmount = previousTotalDue
                .subtract(newTotalDue);

        if (account.getTotalPaid().compareTo(newTotalDue) > 0) {
            // Student paid more than they now owe
            // The excess becomes a credit for next semester
            creditToGenerate = account.getTotalPaid()
                    .subtract(newTotalDue);
        }

        // Create credit balance only if student actually overpaid
        if (creditToGenerate.compareTo(BigDecimal.ZERO) > 0) {
            createCreditBalance(
                    registration.getStudent(),
                    registration.getSemester(),
                    creditToGenerate);

            log.info("Credit balance of ₦{} created for student {} " +
                            "after dropping course {} during add/drop",
                    creditToGenerate,
                    registration.getStudent().getId(),
                    courseCode);
        }

        // Build appropriate message based on scenario
//        String message;
//        if (creditToGenerate.compareTo(BigDecimal.ZERO) > 0) {
//            message = "course " + courseCode +
//                    " dropped successfully. " +
//                    "₦" + creditToGenerate +
//                    " credit has been added to your account " +
//                    "and will be applied to your next semester fees.";
//        } else {
//            message = "course " + courseCode +
//                    " dropped successfully. " +
//                    "your new total fees are ₦" + newTotalDue;
//        }

        String message = creditToGenerate.compareTo(BigDecimal.ZERO) > 0
                ? "Course dropped successfully. ₦" + creditToGenerate + " has been added as a credit balance for next semester."
                : "Course dropped successfully. Your outstanding balance has been reduced by ₦" + refundAmount + ".";

        log.info("Course {} dropped during add/drop for " +
                        "registration {}. Credit generated: ₦{}",
                courseCode, registrationId, creditToGenerate);

        return AddDropResponse.builder()
                .registration(buildResponse(registration))
                .previousTotalDue(previousTotalDue)
                .newTotalDue(newTotalDue)
                .difference(refundAmount.negate())
                .message(message)
                .build();

    }


    private _User resolveStudent(CreateRegistrationRequest request, _User currentUser, boolean isAdmin) {

        if (isAdmin) {
            if (request.getStudentId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Student id is required when creating " +
                                "registration on behalf of a student");
            }
            _User student = userRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Student not found"));

            if (student.getUserRole() != UserRole.STUDENT){
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "selected user is not a student"
                );
            }

            return student;
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

        registration.setTotalCreditUnits(
                registration.getTotalCreditUnits()
                        + totalCarryoverUnits);
        registrationRepository.save(registration);
    }

    private RegistrationItemResponse toItemResponse(
            RegistrationItem item) {
        return RegistrationItemResponse.builder()
                .registrationItemId(item.getId())
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

    private void validateCourseForRegistration(Course course, StudentProfile studentProfile, Registration registration,
                                               UUID registrationId, boolean isCarryover) {
        // Level must match student level
        if (!course.getLevel().equals(studentProfile.getLevel())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "course level " + course.getLevel() +
                            " does not match student level " +
                            studentProfile.getLevel());
        }

        // Carryover courses bypass semester type
        // and department restriction checks
        if (!isCarryover) {

            // Semester type check — skip for SUMMER
            // Summer only has carryover courses
            if (registration.getSemester().getSemesterType() != SemesterType.SUMMER) {

                if (course.getSemesterType() != registration.getSemester().getSemesterType()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "course " + course.getCourseCode() +
                                    " belongs to " +
                                    course.getSemesterType() +
                                    " semester. current active semester is " +
                                    registration.getSemester()
                                            .getSemesterType());
                }
            }

            // Department restriction check
            if (course.getIsDepartmentRestricted()) {
                UUID studentDeptId = studentProfile.getDepartment().getId();
                UUID courseDeptId = course.getDepartment().getId();

                if (!studentDeptId.equals(courseDeptId)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "course " + course.getCourseCode() +
                                    " is restricted to " +
                                    course.getDepartment().getName() +
                                    " department students only");
                }
            }
        }

        // Duplicate check — always enforced
        // even for carryovers
        if (registrationItemRepository
                .existsByRegistrationIdAndCourseId(
                        registrationId, course.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "course already added to this registration");
        }

        // Credit unit limit check — always enforced
        int projectedTotal = registration.getTotalCreditUnits()
                + course.getCreditUnits();

        if (projectedTotal > getMaxCreditUnits()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "adding this course would exceed the maximum " +
                            "of " + getMaxCreditUnits() + " credit units. " +
                            "projected total: " + projectedTotal);
        }
    }

    private int getMaxCreditUnits() {
        return systemConfigService
                .getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS);
    }

    private int getMinCreditUnits() {
        return systemConfigService
                .getIntValue(SystemConfigKeys.MIN_CREDIT_UNITS);
    }

    private void validateCreditUnitLimits(Integer totalCreditUnits) {
        int max = getMaxCreditUnits();
        int min = getMinCreditUnits();

        if (totalCreditUnits < min) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "total credit units (" + totalCreditUnits +
                            ") is below the minimum of " + min);
        }

        if (totalCreditUnits > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "total credit units (" + totalCreditUnits +
                            ") exceeds the maximum of " + max);
        }
    }

    private void createTuitionAccount(Registration registration) {

        StudentProfile studentProfile = getStudentProfile(
                registration.getStudent().getId());

        FeeStructure feeStructure = feeStructureRepository
                .findBySemesterIdAndProgramId(
                        registration.getSemester().getId(),
                        studentProfile.getProgram().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "fee structure not configured for this " +
                                "semester and program. " +
                                "approval cannot proceed. " +
                                "please ask the bursar to configure fees."));

        // Tuition = credit units × cost per unit
        BigDecimal tuitionAmount = feeStructure
                .getCostPerCreditUnit()
                .multiply(BigDecimal.valueOf(
                        registration.getTotalCreditUnits()));

        // Sum all flat admin charges for this semester
        List<AdminCharge> charges = adminChargeRepository
                .findBySemesterId(
                        registration.getSemester().getId());

        BigDecimal adminChargesTotal = charges.stream()
                .map(AdminCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Find unapplied credit balances from prior semesters
        List<CreditBalance> unappliedCredits =
                creditBalanceRepository
                        .findByStudentIdAndIsAppliedFalse(
                                registration.getStudent().getId());

        BigDecimal creditCarriedForward = unappliedCredits.stream()
                .map(CreditBalance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate total due
        BigDecimal totalDue = tuitionAmount
                .add(adminChargesTotal)
                .subtract(creditCarriedForward);

        // Total due cannot go below zero
        if (totalDue.compareTo(BigDecimal.ZERO) < 0) {
            totalDue = BigDecimal.ZERO;
        }

        int minPaymentPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        BigDecimal minimumPayment = totalDue
                .multiply(BigDecimal.valueOf(minPaymentPercentage))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);

        TuitionAccount tuitionAccount = TuitionAccount.builder()
                .registration(registration)
                .student(registration.getStudent())
                .semester(registration.getSemester())
                .tuitionAmount(tuitionAmount)
                .adminChargesTotal(adminChargesTotal)
                .waiverAmount(BigDecimal.ZERO)
                .creditCarriedForward(creditCarriedForward)
                .totalDue(totalDue)
                .totalPaid(BigDecimal.ZERO)
                .minimumPayment(minimumPayment)
                .balance(totalDue)
                .tuitionAccountStatus(TuitionAccountStatus.PENDING)
                .build();

        tuitionRepository.save(tuitionAccount);

        // Mark credit balances as applied
        unappliedCredits.forEach(credit -> {
            credit.setIsApplied(true);
            credit.setAppliedAt(LocalDateTime.now());
            creditBalanceRepository.save(credit);
        });
    }

    private StudentProfile getStudentProfile(UUID studentId) {
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "student profile not found"));
    }

    private BulkAddCourseResponse.FailedCourse buildFailedCourse(
            UUID courseId, String courseCode, String reason) {
        return BulkAddCourseResponse.FailedCourse.builder()
                .courseId(courseId)
                .courseCode(courseCode)
                .reason(reason)
                .build();
    }

    private void validateAddDropWindow(Semester semester) {
        LocalDate today = LocalDate.now();

        // Dean must have configured the window first
        if (semester.getAddDropStart() == null ||
                semester.getAddDropEnd() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "add/drop window has not been configured " +
                            "for this semester. contact the Dean.");
        }

        // Too early — window has not opened yet
        if (today.isBefore(semester.getAddDropStart())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "add/drop window has not opened yet. " +
                            "it opens on " + semester.getAddDropStart());
        }

        // Too late — window has closed
        if (today.isAfter(semester.getAddDropEnd())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "add/drop window has closed. " +
                            "it closed on " + semester.getAddDropEnd());
        }
    }

    private BigDecimal recalculateTuitionForAddDrop(
            TuitionAccount account,
            Registration registration,
            StudentProfile studentProfile) {

        // Get current fee structure for this program/semester
        FeeStructure feeStructure = feeStructureRepository
                .findBySemesterIdAndProgramId(
                        registration.getSemester().getId(),
                        studentProfile.getProgram().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "fee structure not found for this semester"));

        // Recalculate tuition based on NEW credit unit count
        BigDecimal newTuitionAmount = feeStructure
                .getCostPerCreditUnit()
                .multiply(BigDecimal.valueOf(
                        registration.getTotalCreditUnits()));

        account.setTuitionAmount(newTuitionAmount);

        // Recalculate total due
        // total_due = tuition + admin_charges
        //             - waiver_amount - credit_carried_forward
        BigDecimal newTotalDue = newTuitionAmount
                .add(account.getAdminChargesTotal())
                .subtract(account.getWaiverAmount())
                .subtract(account.getCreditCarriedForward());

        if (newTotalDue.compareTo(BigDecimal.ZERO) < 0) {
            newTotalDue = BigDecimal.ZERO;
        }

        account.setTotalDue(newTotalDue);

        // Recalculate minimum payment
        int minPercentage = systemConfigService.getIntValue(
                SystemConfigKeys.MIN_PAYMENT_PERCENTAGE);

        BigDecimal newMinimumPayment = newTotalDue
                .multiply(BigDecimal.valueOf(minPercentage))
                .divide(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.CEILING);

        account.setMinimumPayment(newMinimumPayment);

        // Recalculate balance — what is still owed
        BigDecimal newBalance = newTotalDue
                .subtract(account.getTotalPaid());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }

        account.setBalance(newBalance);

        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            // Either fully paid or overpaid
            // Both result in zero balance
            account.setTuitionAccountStatus(TuitionAccountStatus.PAID);

        } else if (account.getTotalPaid().compareTo(
                newMinimumPayment) >= 0) {
            // Still meets minimum threshold
            account.setTuitionAccountStatus(TuitionAccountStatus.PARTIAL);

        } else {
            // Dropping a course reduced total due but
            // student has not yet met minimum payment
            // on the new amount
            account.setTuitionAccountStatus(TuitionAccountStatus.ADJUSTED);
        }

        tuitionRepository.save(account);

        return newTotalDue;
    }


    private TuitionAccount getTuitionAccount(UUID registrationId) {
        return tuitionRepository
                .findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "tuition account not found for " +
                                "this registration"));
    }

    private void ensureStudentOwnsRegistration(_User currentUser, Registration registration) {
        if (RoleChecker.is_student(currentUser) &&
                !registration.getStudent().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only perform this action on your own registration");
        }
    }

    private void createCreditBalance(
            _User student,
            Semester sourceSemester,
            BigDecimal amount) {

        CreditBalance creditBalance = CreditBalance.builder()
                .student(student)
                .sourceSemester(sourceSemester)
                // targetSemester is null until next registration
                // is created — applied automatically then
                .amount(amount)
                .isApplied(false)
                .build();

        creditBalanceRepository.save(creditBalance);

        log.info("Credit balance of ₦{} created for student {} " +
                        "from semester {}",
                amount,
                student.getId(),
                sourceSemester.getId());

    }
}
