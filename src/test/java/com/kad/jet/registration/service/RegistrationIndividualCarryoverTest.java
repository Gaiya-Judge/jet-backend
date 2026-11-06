package com.kad.jet.registration.service;

import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.course.model.Course;
import com.kad.jet.course.model.CourseType;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.department.model.Department;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.CreditBalanceRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.notification.event.AddDropCourseEvent;
import com.kad.jet.notification.event.RegistrationApprovedEvent;
import com.kad.jet.notification.event.RegistrationRejectedEvent;
import com.kad.jet.notification.event.RegistrationSubmittedEvent;
import com.kad.jet.registration.dto.AddCourseRequest;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationIndividualCarryoverTest {

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
    private StudentProfile profile;
    private Department studentDepartment;

    @BeforeEach
    void setUp() {
        student = _User.builder().id(UUID.randomUUID())
                .userRole(UserRole.STUDENT).build();
        studentDepartment = Department.builder().id(UUID.randomUUID())
                .name("Student Department").build();
        profile = StudentProfile.builder().id(student.getId())
                .level(100).department(studentDepartment).build();
        lenient().when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(studentProfileRepository.findById(student.getId()))
                .thenReturn(Optional.of(profile));
        lenient().when(registrationItemRepository.findByRegistrationId(any()))
                .thenReturn(List.of());
        lenient().when(systemConfigService.getIntValue(any())).thenReturn(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"D", "E", "F"})
    void individualFailingGradeCarryoverIsMandatoryAndNonDroppable(String result) {
        Course course = course(SemesterType.FIRST);
        Registration registration = registration(SemesterType.FIRST, 0);
        prepareAdd(registration, course, List.of(grade(course, result)));

        service.addCourse(student, registration.getId(), request(course));

        RegistrationItem item = savedItems().get(0);
        assertEquals(course.getId(), item.getCourse().getId());
        assertTrue(item.getIsCarryOver());
        assertFalse(item.getIsDroppable());
        assertEquals(3, registration.getTotalCreditUnits());
    }

    @ParameterizedTest
    @CsvSource({"FIRST,SECOND", "SECOND,FIRST"})
    void carryoverIsRejectedInTheOppositeRegularSemester(
            SemesterType original, SemesterType target) {
        Course course = course(original);
        Registration registration = registration(target, 0);
        prepareAdd(registration, course, List.of(grade(course, "F")));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addCourse(student, registration.getId(), request(course)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(registrationItemRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIRST", "SECOND"})
    void carryoverIsAcceptedInItsCorrespondingRegularSemester(String semesterName) {
        SemesterType type = SemesterType.valueOf(semesterName);
        Course course = course(type);
        Registration registration = registration(type, 0);
        prepareAdd(registration, course, List.of(grade(course, "F")));

        assertDoesNotThrow(() -> service.addCourse(student, registration.getId(), request(course)));
        assertTrue(savedItems().get(0).getIsCarryOver());
        assertFalse(savedItems().get(0).getIsDroppable());
    }

    @Test
    void eligibleCarryoverIsAcceptedInSummer() {
        Course course = course(SemesterType.SECOND);
        Registration registration = registration(SemesterType.SUMMER, 0);
        prepareAdd(registration, course, List.of(grade(course, "E")));

        assertDoesNotThrow(() -> service.addCourse(student, registration.getId(), request(course)));
        assertTrue(savedItems().get(0).getIsCarryOver());
        assertFalse(savedItems().get(0).getIsDroppable());
    }

    @Test
    void ordinaryCourseIsRejectedInSummer() {
        Course course = course(SemesterType.FIRST);
        Registration registration = registration(SemesterType.SUMMER, 0);
        prepareAdd(registration, course, List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addCourse(student, registration.getId(), request(course)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void departmentRestrictedEligibleCarryoverIsAccepted() {
        Course course = course(SemesterType.FIRST);
        course.setIsDepartmentRestricted(true);
        course.setDepartment(Department.builder().id(UUID.randomUUID())
                .name("Other Department").build());
        Registration registration = registration(SemesterType.FIRST, 0);
        prepareAdd(registration, course, List.of(grade(course, "D")));

        assertDoesNotThrow(() -> service.addCourse(student, registration.getId(), request(course)));
        assertTrue(savedItems().get(0).getIsCarryOver());
        assertFalse(savedItems().get(0).getIsDroppable());
    }

    @Test
    void automaticallyAddedCarryoverCannotBeAddedAgain() {
        Course course = course(SemesterType.FIRST);
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, course, List.of(grade(course, "F")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                registration.getId(), course.getId())).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addCourse(student, registration.getId(), request(course)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void maximumCheckIncludesExistingAutomaticCarryoverUnits() {
        Course automaticCarryover = course(SemesterType.FIRST);
        Course requested = course(SemesterType.FIRST);
        requested.setId(UUID.randomUUID());
        requested.setCourseCode("JET102");
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, requested,
                List.of(grade(automaticCarryover, "F")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                registration.getId(), automaticCarryover.getId())).thenReturn(true);
        when(systemConfigService.getIntValue(any())).thenReturn(5);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addCourse(student, registration.getId(), request(requested)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void passingCourseIsAddedAsOrdinaryNotCarryover() {
        Course course = course(SemesterType.FIRST);
        Registration registration = registration(SemesterType.FIRST, 0);
        prepareAdd(registration, course, List.of(grade(course, "C")));

        service.addCourse(student, registration.getId(), request(course));

        RegistrationItem item = savedItems().get(0);
        assertFalse(item.getIsCarryOver());
        assertTrue(item.getIsDroppable());
    }

    @Test
    void ordinaryAdditionSynchronizesMissingCarryoversFirst() {
        Course carryover = course(SemesterType.FIRST);
        Course requested = course(SemesterType.FIRST);
        requested.setId(UUID.randomUUID());
        requested.setCourseCode("JET102");
        Registration registration = registration(SemesterType.FIRST, 0);
        prepareAdd(registration, requested, List.of(grade(carryover, "F")));

        service.addCourse(student, registration.getId(), request(requested));

        List<RegistrationItem> saved = savedItems();
        assertEquals(2, saved.size());
        assertEquals(carryover.getId(), saved.get(0).getCourse().getId());
        assertTrue(saved.get(0).getIsCarryOver());
        assertFalse(saved.get(0).getIsDroppable());
        assertEquals(requested.getId(), saved.get(1).getCourse().getId());
        assertFalse(saved.get(1).getIsCarryOver());
    }

    @Test
    void synchronizationDoesNotDuplicateExistingCarryoverOrDuplicateGrades() {
        Course existingCarryover = course(SemesterType.FIRST);
        Course missingCarryover = course(SemesterType.FIRST);
        missingCarryover.setId(UUID.randomUUID());
        missingCarryover.setCourseCode("JET102");
        Course requested = course(SemesterType.FIRST);
        requested.setId(UUID.randomUUID());
        requested.setCourseCode("JET103");
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, requested, List.of(
                grade(existingCarryover, "F"), grade(existingCarryover, "D"),
                grade(missingCarryover, "E")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                registration.getId(), existingCarryover.getId())).thenReturn(true);

        service.addCourse(student, registration.getId(), request(requested));

        List<RegistrationItem> saved = savedItems();
        assertEquals(2, saved.size());
        assertEquals(missingCarryover.getId(), saved.get(0).getCourse().getId());
        assertEquals(requested.getId(), saved.get(1).getCourse().getId());
        verify(registrationItemRepository, times(1)).existsByRegistrationIdAndCourseId(
                registration.getId(), existingCarryover.getId());
    }

    @Test
    void compulsoryCarryoversOverMaximumThrowTask7ConflictBeforeAnySave() {
        Course firstCarryover = course(SemesterType.FIRST);
        Course secondCarryover = course(SemesterType.FIRST);
        secondCarryover.setId(UUID.randomUUID());
        secondCarryover.setCourseCode("JET102");
        Course requested = course(SemesterType.FIRST);
        requested.setId(UUID.randomUUID());
        requested.setCourseCode("JET103");
        Registration registration = registration(SemesterType.FIRST, 0);
        prepareAdd(registration, requested, List.of(
                grade(firstCarryover, "F"), grade(secondCarryover, "E")));
        when(systemConfigService.getIntValue(any())).thenReturn(5);

        var error = assertThrows(
                com.kad.jet.exception.CarryoverCreditLimitException.class,
                () -> service.addCourse(student, registration.getId(), request(requested)));

        assertEquals(5, error.getMaximumCreditUnits());
        assertEquals(6, error.getTotalCarryoverCreditUnits());
        assertEquals(2, error.getCourses().size());
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void ordinaryCourseOverMaximumDoesNotRemoveExistingCompulsoryCarryovers() {
        Course carryover = course(SemesterType.FIRST);
        Course requested = course(SemesterType.FIRST);
        requested.setId(UUID.randomUUID());
        requested.setCourseCode("JET102");
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, requested, List.of(grade(carryover, "F")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                registration.getId(), carryover.getId())).thenReturn(true);
        when(systemConfigService.getIntValue(any())).thenReturn(5);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addCourse(student, registration.getId(), request(requested)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertEquals(3, registration.getTotalCreditUnits());
        verify(registrationItemRepository, never()).delete(any());
        verify(registrationItemRepository, never()).save(any());
    }

    private void prepareAdd(Registration registration, Course course,
                            List<StudentGrade> grades) {
        when(registrationRepository.findById(registration.getId()))
                .thenReturn(Optional.of(registration));
        when(courseRepository.findById(course.getId()))
                .thenReturn(Optional.of(course));
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(grades);
        lenient().when(systemConfigService.getIntValue(any())).thenReturn(30);
    }

    private List<RegistrationItem> savedItems() {
        ArgumentCaptor<RegistrationItem> captor =
                ArgumentCaptor.forClass(RegistrationItem.class);
        verify(registrationItemRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private AddCourseRequest request(Course course) {
        AddCourseRequest request = new AddCourseRequest();
        request.setCourseId(course.getId());
        return request;
    }

    private Registration registration(SemesterType type, int units) {
        return Registration.builder().id(UUID.randomUUID()).student(student)
                .semester(Semester.builder().id(UUID.randomUUID())
                        .semesterType(type).status(SemesterStatus.ACTIVE).build())
                .status(RegistrationStatus.DRAFT).totalCreditUnits(units).build();
    }

    private Course course(SemesterType type) {
        return Course.builder().id(UUID.randomUUID()).courseCode("JET101")
                .title("Course").courseType(CourseType.COMPULSORY).level(100)
                .creditUnits(3).semesterType(type)
                .isDepartmentRestricted(false).isActive(true).build();
    }

    private StudentGrade grade(Course course, String result) {
        return StudentGrade.builder().id(UUID.randomUUID()).student(student)
                .course(course).grade(result).build();
    }
}
