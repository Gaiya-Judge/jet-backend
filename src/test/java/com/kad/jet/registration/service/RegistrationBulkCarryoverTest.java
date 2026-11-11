package com.kad.jet.registration.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.course.model.Course;
import com.kad.jet.course.model.CourseType;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.department.model.Department;
import com.kad.jet.exception.CarryoverCreditLimitException;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.CreditBalanceRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.notification.event.AddDropCourseEvent;
import com.kad.jet.notification.event.RegistrationApprovedEvent;
import com.kad.jet.notification.event.RegistrationRejectedEvent;
import com.kad.jet.notification.event.RegistrationSubmittedEvent;
import com.kad.jet.registration.dto.BulkAddCourseRequest;
import com.kad.jet.registration.dto.BulkAddCourseResponse;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.model.StudentGrade;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentGradeRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationBulkCarryoverTest {

    @Mock RegistrationRepository registrationRepository;
    @Mock RegistrationItemRepository registrationItemRepository;
    @Mock CourseRepository courseRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock StudentGradeRepository studentGradeRepository;
    @Mock StudentProfileRepository studentProfileRepository;
    @Mock TuitionAccountRepository tuitionRepository;
    @Mock FeeStructureRepository feeStructureRepository;
    @Mock AdminChargeRepository adminChargeRepository;
    @Mock CreditBalanceRepository creditBalanceRepository;
    @Mock UserRepository userRepository;
    @Mock SystemConfigService systemConfigService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks RegistrationServiceImp service;

    private _User student;
    private Registration registration;
    private StudentProfile profile;
    private Department studentDepartment;
    private List<RegistrationItem> existingItems;
    private List<RegistrationItem> savedItems;

    @BeforeEach
    void setUp() {
        student = _User.builder().id(UUID.randomUUID()).fullName("Student")
                .userRole(UserRole.STUDENT).build();
        Semester semester = semester(SemesterType.FIRST);
        registration = Registration.builder().id(UUID.randomUUID()).student(student)
                .semester(semester).status(RegistrationStatus.DRAFT)
                .totalCreditUnits(0).build();
        studentDepartment = Department.builder().id(UUID.randomUUID()).name("Student Dept").build();
        profile = StudentProfile.builder().id(student.getId()).level(100)
                .department(studentDepartment).build();
        existingItems = new ArrayList<>();
        savedItems = new ArrayList<>();

        lenient().when(registrationRepository.findById(registration.getId()))
                .thenReturn(Optional.of(registration));
        lenient().when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(studentProfileRepository.findById(student.getId()))
                .thenReturn(Optional.of(profile));
        lenient().when(registrationItemRepository.findByRegistrationId(registration.getId()))
                .thenAnswer(invocation -> new ArrayList<>(existingItems));
        lenient().when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                eq(registration.getId()), any()))
                .thenAnswer(invocation -> existingItems.stream().anyMatch(item ->
                        item.getCourse().getId().equals(invocation.getArgument(1))));
        lenient().when(registrationItemRepository.save(any(RegistrationItem.class)))
                .thenAnswer(invocation -> {
                    RegistrationItem item = invocation.getArgument(0);
                    existingItems.add(item);
                    savedItems.add(item);
                    return item;
                });
        lenient().when(studentGradeRepository.findByStudentId(student.getId())).thenReturn(List.of());
        lenient().when(systemConfigService.getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS)).thenReturn(30);
    }

    @Test
    void compulsoryCarryoverAggregateIncludesExistingAndNewCoursesBeforeSaving() {
        Course existing = course(SemesterType.FIRST, 3, "OLD101");
        Course missing = course(SemesterType.FIRST, 3, "NEW101");
        existingItems.add(item(existing, true, false));
        registration.setTotalCreditUnits(3);
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(missing, "F")));
        when(systemConfigService.getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS)).thenReturn(5);

        CarryoverCreditLimitException error = assertThrows(CarryoverCreditLimitException.class,
                () -> service.autoAddCarryoverCourses(registration, student));

        assertEquals(5, error.getMaximumCreditUnits());
        assertEquals(6, error.getTotalCarryoverCreditUnits());
        assertEquals(2, error.getCourses().size());
        assertEquals(3, registration.getTotalCreditUnits());
        verify(registrationItemRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void alreadyRegisteredEligibleCourseIsPromotedWithoutDoubleCountingUnits() {
        Course course = course(SemesterType.FIRST, 3, "CAR101");
        RegistrationItem existingOrdinary = item(course, false, true);
        existingItems.add(existingOrdinary);
        registration.setTotalCreditUnits(3);
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(course, "F")));

        service.autoAddCarryoverCourses(registration, student);

        assertTrue(existingOrdinary.getIsCarryOver());
        assertFalse(existingOrdinary.getIsDroppable());
        assertEquals(3, registration.getTotalCreditUnits());
        assertEquals(1, savedItems.size());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void noEligibleOrExistingCarryoversAvoidCreditLimitLookup() {
        service.autoAddCarryoverCourses(registration, student);

        verify(systemConfigService, never()).getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS);
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void bulkCarryoverLimitExceptionEscapesBeforeAnyRegistrationMutation() {
        Course first = course(SemesterType.FIRST, 3, "CAR101");
        Course second = course(SemesterType.FIRST, 3, "CAR102");
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(first, "F"), grade(second, "E")));
        when(systemConfigService.getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS)).thenReturn(5);

        CarryoverCreditLimitException error = assertThrows(CarryoverCreditLimitException.class,
                () -> service.addCourses(student, registration.getId(),
                        request(List.of(first.getId(), second.getId()))));

        assertEquals(6, error.getTotalCarryoverCreditUnits());
        assertTrue(savedItems.isEmpty());
        assertEquals(0, registration.getTotalCreditUnits());
        verify(registrationRepository, never()).save(any());
    }

    static Stream<List<UUID>> bulkOrderings() {
        UUID carryoverId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ordinaryId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return Stream.of(List.of(carryoverId, ordinaryId), List.of(ordinaryId, carryoverId));
    }

    @ParameterizedTest
    @MethodSource("bulkOrderings")
    void carryoversSynchronizeBeforeOrdinaryCoursesRegardlessOfRequestOrder(List<UUID> order) {
        Course carryover = course(SemesterType.FIRST, 3, "CAR101");
        Course ordinary = course(SemesterType.FIRST, 3, "ORD101");
        carryover.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ordinary.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        courses(carryover, ordinary);
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(carryover, "F")));

        BulkAddCourseResponse response = service.addCourses(student, registration.getId(), request(order));

        assertEquals(2, savedItems.size());
        assertEquals(carryover.getId(), savedItems.getFirst().getCourse().getId());
        assertTrue(savedItems.getFirst().getIsCarryOver());
        assertFalse(savedItems.getFirst().getIsDroppable());
        assertEquals(ordinary.getId(), savedItems.get(1).getCourse().getId());
        assertEquals(order, response.getAdded());
        assertEquals(6, response.getTotalCreditUnits());
    }

    @Test
    void eligibleDepartmentRestrictedCarryoverIsAcceptedAndNonDroppable() {
        Course carryover = course(SemesterType.FIRST, 3, "CAR101");
        carryover.setIsDepartmentRestricted(true);
        carryover.setDepartment(Department.builder().id(UUID.randomUUID()).name("Other Dept").build());
        courses(carryover);
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(carryover, "D")));

        BulkAddCourseResponse response = service.addCourses(student, registration.getId(), request(carryover.getId()));

        assertEquals(List.of(carryover.getId()), response.getAdded());
        assertEquals(1, savedItems.size());
        assertTrue(savedItems.getFirst().getIsCarryOver());
        assertFalse(savedItems.getFirst().getIsDroppable());
    }

    @Test
    void ordinaryCourseStillFailsDepartmentRestriction() {
        Course ordinary = course(SemesterType.FIRST, 3, "ORD101");
        ordinary.setIsDepartmentRestricted(true);
        ordinary.setDepartment(Department.builder().id(UUID.randomUUID()).name("Other Dept").build());
        courses(ordinary);
        when(studentGradeRepository.findByStudentId(student.getId())).thenReturn(List.of());

        BulkAddCourseResponse response = service.addCourses(student, registration.getId(), request(ordinary.getId()));

        assertTrue(response.getAdded().isEmpty());
        assertEquals(1, response.getFailed().size());
        assertTrue(response.getFailed().getFirst().getReason().contains("restricted"));
        assertTrue(savedItems.isEmpty());
    }

    @Test
    void repeatedRequestedIdsAndExistingItemsDoNotCreateDuplicates() {
        Course ordinary = course(SemesterType.FIRST, 3, "ORD101");
        courses(ordinary);
        when(studentGradeRepository.findByStudentId(student.getId())).thenReturn(List.of());

        BulkAddCourseResponse duplicates = service.addCourses(student, registration.getId(),
                request(List.of(ordinary.getId(), ordinary.getId())));
        assertEquals(List.of(ordinary.getId()), duplicates.getAdded());
        assertEquals(1, duplicates.getFailed().size());
        assertEquals(1, savedItems.size());

        RegistrationItem existing = item(course(SemesterType.FIRST, 3, "EX101"), false, true);
        existingItems.add(existing);
        courses(existing.getCourse());
        BulkAddCourseResponse alreadyPresent = service.addCourses(student, registration.getId(),
                request(existing.getCourse().getId()));
        assertTrue(alreadyPresent.getAdded().isEmpty());
        assertEquals(1, alreadyPresent.getFailed().size());
        assertEquals(1, savedItems.size());
    }

    @Test
    void summerAcceptsEligibleCarryoverButRejectsOrdinaryCourse() {
        registration.setSemester(semester(SemesterType.SUMMER));
        Course carryover = course(SemesterType.SECOND, 3, "CAR201");
        Course ordinary = course(SemesterType.FIRST, 3, "ORD201");
        courses(carryover, ordinary);
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(carryover, "E")));

        BulkAddCourseResponse response = service.addCourses(student, registration.getId(),
                request(List.of(carryover.getId(), ordinary.getId())));

        assertEquals(List.of(carryover.getId()), response.getAdded());
        assertEquals(1, response.getFailed().size());
        assertEquals(1, savedItems.size());
        assertTrue(savedItems.getFirst().getIsCarryOver());
    }

    @Test
    void invalidOrdinaryCourseDoesNotPreventAnotherValidCourse() {
        Course restricted = course(SemesterType.FIRST, 3, "BAD101");
        restricted.setIsDepartmentRestricted(true);
        restricted.setDepartment(Department.builder().id(UUID.randomUUID()).name("Other Dept").build());
        Course valid = course(SemesterType.FIRST, 3, "GOOD101");
        courses(restricted, valid);
        when(studentGradeRepository.findByStudentId(student.getId())).thenReturn(List.of());

        BulkAddCourseResponse response = service.addCourses(student, registration.getId(),
                request(List.of(restricted.getId(), valid.getId())));

        assertEquals(List.of(valid.getId()), response.getAdded());
        assertEquals(1, response.getFailed().size());
        assertEquals(valid.getId(), savedItems.getFirst().getCourse().getId());
        assertEquals(3, registration.getTotalCreditUnits());
    }

    @Test
    void oppositeRegularSemesterCarryoverIsRejected() {
        Course carryover = course(SemesterType.SECOND, 3, "CAR201");
        courses(carryover);
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(carryover, "F")));

        BulkAddCourseResponse response = service.addCourses(student, registration.getId(),
                request(carryover.getId()));

        assertTrue(response.getAdded().isEmpty());
        assertEquals(1, response.getFailed().size());
        assertTrue(savedItems.isEmpty());
    }

    @Test
    void outstandingSecondSemesterCourseIsNotSynchronizedIntoFirstRegistration() {
        Course secondSemesterCourse = course(SemesterType.SECOND, 3, "CAR201");
        registration.setSemester(semester(SemesterType.FIRST));
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(secondSemesterCourse, "F")));

        service.autoAddCarryoverCourses(registration, student);

        assertTrue(savedItems.isEmpty());
        verify(systemConfigService, never())
                .getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS);
    }

    @Test
    void outstandingFirstSemesterCourseIsNotSynchronizedIntoSecondRegistration() {
        registration.setSemester(semester(SemesterType.SECOND));
        Course firstSemesterCourse = course(SemesterType.FIRST, 3, "CAR101");
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "E")));

        service.autoAddCarryoverCourses(registration, student);

        assertTrue(savedItems.isEmpty());
        verify(systemConfigService, never())
                .getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS);
    }

    private void courses(Course... courses) {
        for (Course course : courses) {
            when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        }
    }

    private Course course(SemesterType type, int units, String code) {
        return Course.builder().id(UUID.randomUUID()).courseCode(code).title(code)
                .courseType(CourseType.COMPULSORY).level(100).creditUnits(units)
                .semesterType(type).isDepartmentRestricted(false).build();
    }

    private RegistrationItem item(Course course, boolean carryover, boolean droppable) {
        return RegistrationItem.builder().registration(registration).course(course)
                .isCarryOver(carryover).isDroppable(droppable).build();
    }

    private StudentGrade grade(Course course, String grade) {
        return StudentGrade.builder().id(UUID.randomUUID()).student(student)
                .course(course).grade(grade).build();
    }

    private Semester semester(SemesterType type) {
        return Semester.builder().id(UUID.randomUUID()).semesterType(type)
                .academicYear("2025/2026").build();
    }

    private BulkAddCourseRequest request(UUID... courseIds) {
        return request(List.of(courseIds));
    }

    private BulkAddCourseRequest request(List<UUID> courseIds) {
        BulkAddCourseRequest request = new BulkAddCourseRequest();
        request.setCourseIds(courseIds);
        return request;
    }
}
