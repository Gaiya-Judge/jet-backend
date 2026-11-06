package com.kad.jet.registration.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.course.model.Course;
import com.kad.jet.course.model.CourseType;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.CreditBalanceRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.model.StudentGrade;
import com.kad.jet.student.repository.StudentGradeRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationCarryoverTest {

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
    private Course firstSemesterCourse;
    private Course secondSemesterCourse;
    private Semester firstSemester;
    private Semester secondSemester;
    private Semester summer;

    @BeforeEach
    void setUp() {
        student = _User.builder().id(UUID.randomUUID()).build();
        firstSemesterCourse = course(SemesterType.FIRST);
        secondSemesterCourse = course(SemesterType.SECOND);
        firstSemester = semester(SemesterType.FIRST);
        secondSemester = semester(SemesterType.SECOND);
        summer = semester(SemesterType.SUMMER);
    }

    @Test
    void failedCourseIsEligibleInItsOriginalSemesterAndSummerOnly() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(
                        grade(firstSemesterCourse, "F", summer),
                        grade(secondSemesterCourse, "F", firstSemester)));

        assertTrue(service.isCarryoverEligible(student, firstSemesterCourse,
                firstSemester));
        assertTrue(service.isCarryoverEligible(student, firstSemesterCourse,
                summer));
        assertFalse(service.isCarryoverEligible(student, firstSemesterCourse,
                semester(SemesterType.SECOND)));

        assertFalse(service.isCarryoverEligible(student, secondSemesterCourse,
                firstSemester));
        assertTrue(service.isCarryoverEligible(student, secondSemesterCourse,
                secondSemester));
        assertTrue(service.isCarryoverEligible(student, secondSemesterCourse,
                summer));
    }

    @Test
    void completedCourseAndCourseWithoutOutstandingGradeAreNotCarryovers() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F"),
                        grade(firstSemesterCourse, "C")))
                .thenReturn(List.of(grade(firstSemesterCourse, "A"),
                                grade(firstSemesterCourse, "B"),
                                grade(firstSemesterCourse, "C")))
                .thenReturn(List.of());
        assertFalse(service.isCarryoverEligible(student, firstSemesterCourse,
                firstSemester));
        assertFalse(service.isCarryoverEligible(student, firstSemesterCourse,
                firstSemester));
        assertFalse(service.isCarryoverEligible(student, firstSemesterCourse,
                summer));
    }

    @Test
    void eachFailingGradeQualifiesForCarryoverIndividually() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "D", summer)))
                .thenReturn(List.of(grade(firstSemesterCourse, "E", summer)))
                .thenReturn(List.of(grade(firstSemesterCourse, "F", summer)));

        assertTrue(service.isCarryoverEligible(student, firstSemesterCourse,
                firstSemester));
        assertTrue(service.isCarryoverEligible(student, firstSemesterCourse,
                firstSemester));
        assertTrue(service.isCarryoverEligible(student, firstSemesterCourse,
                firstSemester));
    }

    @Test
    void draftCarryoverIsAutomaticallyAddedAsMandatoryAndOnlyOnce() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "D"),
                        grade(firstSemesterCourse, "E")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                any(), any())).thenReturn(false);
        when(systemConfigService.getIntValue(any())).thenReturn(30);

        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .status(RegistrationStatus.DRAFT).totalCreditUnits(0).build();

        service.autoAddCarryoverCourses(registration, student);

        ArgumentCaptor<RegistrationItem> captor =
                ArgumentCaptor.forClass(RegistrationItem.class);
        verify(registrationItemRepository, times(1)).save(captor.capture());
        assertTrue(captor.getValue().getIsCarryOver());
        assertFalse(captor.getValue().getIsDroppable());
        assertEquals(firstSemesterCourse.getCreditUnits(),
                registration.getTotalCreditUnits());
    }

    @Test
    void allEligibleCarryoversWithinMaximumAreAdded() {
        Course anotherCourse = course(SemesterType.FIRST);
        anotherCourse.setCourseCode("JET102");
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F"),
                        grade(anotherCourse, "D")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                any(), any())).thenReturn(false);
        when(systemConfigService.getIntValue(any())).thenReturn(6);
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .totalCreditUnits(0).build();

        service.autoAddCarryoverCourses(registration, student);

        ArgumentCaptor<RegistrationItem> captor =
                ArgumentCaptor.forClass(RegistrationItem.class);
        verify(registrationItemRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(
                item -> item.getIsCarryOver() && !item.getIsDroppable()));
        assertEquals(6, registration.getTotalCreditUnits());
    }

    @Test
    void carryoversExactlyAtMaximumAreAccepted() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                any(), any())).thenReturn(false);
        when(systemConfigService.getIntValue(any())).thenReturn(3);
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .totalCreditUnits(0).build();

        assertDoesNotThrow(() -> service.autoAddCarryoverCourses(registration, student));

        verify(registrationItemRepository).save(any());
        assertEquals(3, registration.getTotalCreditUnits());
    }

    @Test
    void excessiveCarryoversThrowDetailsBeforeSavingAnyItems() {
        Course anotherCourse = course(SemesterType.FIRST);
        anotherCourse.setCourseCode("JET102");
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F"),
                        grade(anotherCourse, "E")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                any(), any())).thenReturn(false);
        when(systemConfigService.getIntValue(any())).thenReturn(5);
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .totalCreditUnits(0).build();

        var error = assertThrows(
                com.kad.jet.exception.CarryoverCreditLimitException.class,
                () -> service.autoAddCarryoverCourses(registration, student));

        assertEquals(5, error.getMaximumCreditUnits());
        assertEquals(6, error.getTotalCarryoverCreditUnits());
        assertEquals(2, error.getCourses().size());
        assertTrue(error.getCourses().stream().anyMatch(course ->
                course.getCourseId().equals(firstSemesterCourse.getId())
                        && course.getCourseCode().equals("JET101")
                        && course.getCourseTitle().equals("Course")
                        && course.getCreditUnits() == 3));
        verify(registrationItemRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void draftWithoutOutstandingCarryoversAddsNothing() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "B")));
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .totalCreditUnits(0).build();

        service.autoAddCarryoverCourses(registration, student);

        verify(registrationItemRepository, never()).save(any());
        assertEquals(0, registration.getTotalCreditUnits());
    }

    @Test
    void ineligibleSemesterCarryoverDoesNotReadMaximumOrSaveItem() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F")));
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(secondSemester)
                .totalCreditUnits(0).build();

        assertDoesNotThrow(() -> service.autoAddCarryoverCourses(registration, student));

        verify(systemConfigService, never())
                .getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS);
        verify(registrationItemRepository, never()).save(any());
    }

    @Test
    void automaticInsertionDoesNotDuplicateAnExistingItem() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                any(), any())).thenReturn(true);
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .totalCreditUnits(0).build();

        service.autoAddCarryoverCourses(registration, student);

        verify(registrationItemRepository, never()).save(any());
        assertEquals(0, registration.getTotalCreditUnits());
    }

    @Test
    void mandatoryCarryoverCannotBeRemovedFromDraft() {
        student.setUserRole(com.kad.jet.user.model.UserRole.STUDENT);
        Registration registration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(firstSemester)
                .status(RegistrationStatus.DRAFT).totalCreditUnits(3).build();
        RegistrationItem mandatory = RegistrationItem.builder()
                .registration(registration).course(firstSemesterCourse)
                .isCarryOver(true).isDroppable(false).build();
        when(registrationRepository.findById(registration.getId()))
                .thenReturn(Optional.of(registration));
        when(registrationItemRepository.findByRegistrationIdAndCourseId(
                registration.getId(), firstSemesterCourse.getId()))
                .thenReturn(Optional.of(mandatory));

        assertThrows(ResponseStatusException.class, () -> service.removeCourse(
                student, registration.getId(), firstSemesterCourse.getId()));
        verify(registrationItemRepository, never()).delete(any());
    }

    @Test
    void summerRejectsOrdinaryCourseAndAllowsOutstandingCarryover() {
        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of());
        var profile = com.kad.jet.student.model.StudentProfile.builder()
                .level(100).build();
        Course ordinaryCourse = course(SemesterType.SECOND);
        Registration summerRegistration = Registration.builder()
                .id(UUID.randomUUID()).student(student).semester(summer)
                .totalCreditUnits(0).build();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.validateCourseForRegistration(ordinaryCourse,
                        profile, summerRegistration, summerRegistration.getId(),
                        false));
        assertEquals(400, error.getStatusCode().value());

        when(studentGradeRepository.findByStudentId(student.getId()))
                .thenReturn(List.of(grade(firstSemesterCourse, "F")));
        when(registrationItemRepository.existsByRegistrationIdAndCourseId(
                summerRegistration.getId(), firstSemesterCourse.getId()))
                .thenReturn(false);
        when(systemConfigService.getIntValue(any())).thenReturn(30);

        assertDoesNotThrow(() -> service.validateCourseForRegistration(
                firstSemesterCourse, profile, summerRegistration,
                summerRegistration.getId(), false));
    }

    private Course course(SemesterType semesterType) {
        return Course.builder().id(UUID.randomUUID()).courseCode("JET101")
                .title("Course").courseType(CourseType.COMPULSORY).level(100)
                .creditUnits(3).semesterType(semesterType)
                .isDepartmentRestricted(false).isActive(true).build();
    }

    private Semester semester(SemesterType type) {
        return Semester.builder().id(UUID.randomUUID()).semesterType(type)
                .academicYear("2025/2026").build();
    }

    private StudentGrade grade(Course course, String result) {
        return StudentGrade.builder().id(UUID.randomUUID()).student(student)
                .course(course).grade(result).build();
    }

    private StudentGrade grade(Course course, String result, Semester attemptedSemester) {
        return StudentGrade.builder().id(UUID.randomUUID()).student(student)
                .course(course).semester(attemptedSemester).grade(result).build();
    }
}
