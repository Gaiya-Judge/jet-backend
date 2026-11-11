package com.kad.jet.registration.service;

import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.course.model.Course;
import com.kad.jet.course.model.CourseType;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.department.model.Department;
import com.kad.jet.finance.model.FeeStructure;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.model.TuitionAccountStatus;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.CreditBalanceRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.program.model.Program;
import com.kad.jet.registration.dto.AddDropCourseRequest;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationAddDropCarryoverTest {

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
    private Department studentDepartment;
    private StudentProfile profile;

    @BeforeEach
    void setUp() {
        student = _User.builder().id(UUID.randomUUID()).userRole(UserRole.STUDENT).build();
        studentDepartment = Department.builder().id(UUID.randomUUID()).name("Student Dept").build();
        profile = StudentProfile.builder().id(student.getId()).department(studentDepartment)
                .program(Program.builder().id(UUID.randomUUID()).name("Program").build())
                .level(100).build();
        lenient().when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(call -> call.getArgument(0));
        lenient().when(studentProfileRepository.findById(student.getId()))
                .thenReturn(Optional.of(profile));
        lenient().when(registrationItemRepository.findByRegistrationId(any()))
                .thenReturn(List.of());
        lenient().when(tuitionRepository.findByRegistrationIdIn(any()))
                .thenReturn(List.of());
        lenient().when(systemConfigService.getIntValue(any())).thenReturn(30);
    }

    @Test
    void eligibleDepartmentRestrictedCarryoverCanBeAdded() {
        Course course = course(SemesterType.FIRST);
        restrictToOtherDepartment(course);
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, course, List.of(grade(course, "F")));

        assertDoesNotThrow(() -> service.addCourseDuringAddDrop(student,
                registration.getId(), addRequest(course)));

        RegistrationItem added = capturedItem();
        assertTrue(added.getIsCarryOver());
        assertFalse(added.getIsDroppable());
    }

    @Test
    void ordinaryDepartmentRestrictedCourseRequiresMatchingDepartment() {
        Course course = course(SemesterType.FIRST);
        restrictToOtherDepartment(course);
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, course, List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.addCourseDuringAddDrop(student, registration.getId(), addRequest(course)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void oppositeSemesterCarryoverIsRejected() {
        Course course = course(SemesterType.SECOND);
        Registration registration = registration(SemesterType.FIRST, 3);
        prepareAdd(registration, course, List.of(grade(course, "F")));

        assertThrows(ResponseStatusException.class,
                () -> service.addCourseDuringAddDrop(student, registration.getId(), addRequest(course)));
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void validSummerCarryoverIsAccepted() {
        Course course = course(SemesterType.SECOND);
        Registration registration = registration(SemesterType.SUMMER, 3);
        prepareAdd(registration, course, List.of(grade(course, "E")));

        assertDoesNotThrow(() -> service.addCourseDuringAddDrop(student,
                registration.getId(), addRequest(course)));
        assertTrue(capturedItem().getIsCarryOver());
    }

    @Test
    void ordinaryCourseIsRejectedDuringSummer() {
        Course course = course(SemesterType.FIRST);
        Registration registration = registration(SemesterType.SUMMER, 3);
        prepareAdd(registration, course, List.of());

        assertThrows(ResponseStatusException.class,
                () -> service.addCourseDuringAddDrop(student, registration.getId(), addRequest(course)));
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void carryoverFlagPreventsDropEvenWhenDroppableFlagIsTrue() {
        assertRejectedDrop(item(true, true));
    }

    @Test
    void nondroppableOrdinaryItemCannotBeDropped() {
        assertRejectedDrop(item(false, false));
    }

    @Test
    void nullFlagsAreDefensivelyNondroppable() {
        assertRejectedDrop(item(null, null));
    }

    @Test
    void ordinaryDroppableCourseCanBeDropped() {
        RegistrationItem item = item(false, true);
        prepareDrop(item);

        assertDoesNotThrow(() -> service.dropCourseDuringAddDrop(
                student, item.getRegistration().getId(), item.getCourse().getId(), null));

        verify(registrationItemRepository).delete(item);
        assertEquals(3, item.getRegistration().getTotalCreditUnits());
    }

    private void assertRejectedDrop(RegistrationItem item) {
        prepareDrop(item);
        int unitsBefore = item.getRegistration().getTotalCreditUnits();

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.dropCourseDuringAddDrop(student, item.getRegistration().getId(),
                        item.getCourse().getId(), null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(registrationItemRepository, never()).delete(any());
        verify(registrationRepository, never()).save(any());
        assertEquals(unitsBefore, item.getRegistration().getTotalCreditUnits());
    }

    private void prepareAdd(Registration registration, Course course, List<StudentGrade> grades) {
        lenient().when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));
        lenient().when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        lenient().when(studentGradeRepository.findByStudentId(student.getId())).thenReturn(grades);
        TuitionAccount tuitionAccount = account(registration);
        lenient().when(tuitionRepository.findByRegistrationId(registration.getId())).thenReturn(Optional.of(tuitionAccount));
        lenient().when(tuitionRepository.findByRegistrationIdIn(any())).thenReturn(List.of(tuitionAccount));
        lenient().when(feeStructureRepository.findBySemesterIdAndProgramId(any(), any()))
                .thenReturn(Optional.of(FeeStructure.builder().costPerCreditUnit(BigDecimal.TEN).build()));
    }

    private void prepareDrop(RegistrationItem item) {
        Registration registration = item.getRegistration();
        lenient().when(registrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));
        lenient().when(registrationItemRepository.findByRegistrationIdAndCourseId(registration.getId(),
                item.getCourse().getId())).thenReturn(Optional.of(item));
        lenient().when(systemConfigService.getIntValue(any())).thenReturn(0);
        TuitionAccount tuitionAccount = account(registration);
        lenient().when(tuitionRepository.findByRegistrationId(registration.getId())).thenReturn(Optional.of(tuitionAccount));
        lenient().when(tuitionRepository.findByRegistrationIdIn(any())).thenReturn(List.of(tuitionAccount));
        lenient().when(feeStructureRepository.findBySemesterIdAndProgramId(any(), any()))
                .thenReturn(Optional.of(FeeStructure.builder().costPerCreditUnit(BigDecimal.TEN).build()));
    }

    private RegistrationItem capturedItem() {
        ArgumentCaptor<RegistrationItem> captor = ArgumentCaptor.forClass(RegistrationItem.class);
        verify(registrationItemRepository).save(captor.capture());
        return captor.getValue();
    }

    private AddDropCourseRequest addRequest(Course course) {
        AddDropCourseRequest request = new AddDropCourseRequest();
        request.setCourseId(course.getId());
        return request;
    }

    private RegistrationItem item(Boolean carryover, Boolean droppable) {
        Registration registration = registration(SemesterType.FIRST, 6);
        return RegistrationItem.builder().registration(registration)
                .course(course(SemesterType.FIRST)).isCarryOver(carryover).isDroppable(droppable).build();
    }

    private Registration registration(SemesterType type, int units) {
        return Registration.builder().id(UUID.randomUUID()).student(student)
                .semester(Semester.builder().id(UUID.randomUUID()).semesterType(type)
                        .status(SemesterStatus.ACTIVE).addDropStart(LocalDate.now().minusDays(1))
                        .addDropEnd(LocalDate.now().plusDays(1)).build())
                .status(RegistrationStatus.APPROVED).totalCreditUnits(units).build();
    }

    private Course course(SemesterType type) {
        return Course.builder().id(UUID.randomUUID()).courseCode("JET101").title("Course")
                .courseType(CourseType.COMPULSORY).level(100).creditUnits(3).semesterType(type)
                .isDepartmentRestricted(false).isActive(true).build();
    }

    private StudentGrade grade(Course course, String result) {
        return StudentGrade.builder().id(UUID.randomUUID()).student(student).course(course).grade(result).build();
    }

    private void restrictToOtherDepartment(Course course) {
        course.setIsDepartmentRestricted(true);
        course.setDepartment(Department.builder().id(UUID.randomUUID()).name("Other Dept").build());
    }

    private TuitionAccount account(Registration registration) {
        TuitionAccount account = TuitionAccount.builder().id(UUID.randomUUID()).registration(registration)
                .student(student).semester(registration.getSemester()).totalDue(BigDecimal.valueOf(30))
                .totalPaid(BigDecimal.ZERO).adminChargesTotal(BigDecimal.ZERO).waiverAmount(BigDecimal.ZERO)
                .creditCarriedForward(BigDecimal.ZERO).tuitionAccountStatus(TuitionAccountStatus.PENDING).build();
        return account;
    }
}
