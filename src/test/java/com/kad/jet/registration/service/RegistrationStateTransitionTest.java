package com.kad.jet.registration.service;

import com.kad.jet.config.SystemConfigKeys;
import com.kad.jet.config.service.SystemConfigService;
import com.kad.jet.course.repository.CourseRepository;
import com.kad.jet.finance.model.FeeStructure;
import com.kad.jet.finance.model.TuitionAccount;
import com.kad.jet.finance.repository.AdminChargeRepository;
import com.kad.jet.finance.repository.CreditBalanceRepository;
import com.kad.jet.finance.repository.FeeStructureRepository;
import com.kad.jet.finance.repository.TuitionAccountRepository;
import com.kad.jet.notification.event.RegistrationRejectedEvent;
import com.kad.jet.notification.event.RegistrationSubmittedEvent;
import com.kad.jet.registration.dto.CreateRegistrationRequest;
import com.kad.jet.registration.dto.DeanReviewRequest;
import com.kad.jet.registration.dto.RegistrationResponse;
import com.kad.jet.registration.dto.SubmitRegistrationRequest;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationStatus;
import com.kad.jet.registration.repository.RegistrationItemRepository;
import com.kad.jet.registration.repository.RegistrationRepository;
import com.kad.jet.semester.model.Semester;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.model.SemesterType;
import com.kad.jet.semester.repository.SemesterRepository;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentGradeRepository;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import jakarta.transaction.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationStateTransitionTest {

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
    private _User dean;
    private StudentProfile profile;
    private Semester semester;
    private Registration registration;

    @BeforeEach
    void setUp() {
        student = _User.builder().id(UUID.randomUUID()).fullName("Student")
                .userRole(UserRole.STUDENT).build();
        dean = _User.builder().id(UUID.randomUUID()).fullName("Dean")
                .userRole(UserRole.DEAN).build();
        var program = com.kad.jet.program.model.Program.builder()
                .id(UUID.randomUUID()).name("Engineering").build();
        profile = StudentProfile.builder().id(student.getId())
                .matricNumber("JET/001").level(100).program(program).build();
        semester = Semester.builder().id(UUID.randomUUID())
                .academicYear("2026/2027").semesterType(SemesterType.FIRST)
                .status(SemesterStatus.ACTIVE).build();
        registration = Registration.builder().id(UUID.randomUUID())
                .student(student).semester(semester).status(RegistrationStatus.DRAFT)
                .totalCreditUnits(20).build();

        lenient().when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(registrationItemRepository.findByRegistrationId(any()))
                .thenReturn(List.of());
        lenient().when(studentProfileRepository.findById(student.getId()))
                .thenReturn(Optional.of(profile));
        lenient().when(tuitionRepository.findByRegistrationIdIn(any()))
                .thenReturn(List.of());
        lenient().when(systemConfigService.getIntValue(SystemConfigKeys.MIN_CREDIT_UNITS))
                .thenReturn(10);
        lenient().when(systemConfigService.getIntValue(SystemConfigKeys.MAX_CREDIT_UNITS))
                .thenReturn(30);
        lenient().when(registrationRepository.findById(registration.getId()))
                .thenAnswer(invocation -> Optional.of(registration));
    }

    @Test
    void createDraftInitializesDraftStatus() {
        when(semesterRepository.findByStatus(SemesterStatus.ACTIVE))
                .thenReturn(Optional.of(semester));
        when(registrationRepository.existsByStudentIdAndSemesterId(student.getId(), semester.getId()))
                .thenReturn(false);
        when(tuitionRepository.findByStudentId(student.getId())).thenReturn(List.of());
        when(studentGradeRepository.findByStudentId(student.getId())).thenReturn(List.of());
        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation -> {
                    Registration saved = invocation.getArgument(0);
                    saved.setId(registration.getId());
                    return saved;
                });

        RegistrationResponse result = service.createDraft(student, new CreateRegistrationRequest());

        assertEquals(RegistrationStatus.DRAFT, result.getStatus());
        verify(registrationRepository).save(argThat(saved -> saved.getStatus() == RegistrationStatus.DRAFT));
    }

    @Test
    void onlyDraftCanBeSubmitted() {
        registration.setStatus(RegistrationStatus.APPROVED);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.submitRegistration(student, registration.getId(), new SubmitRegistrationRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void successfulSubmissionMovesDraftToPendingDean() {
        RegistrationResponse result = service.submitRegistration(
                student, registration.getId(), new SubmitRegistrationRequest());

        assertEquals(RegistrationStatus.PENDING_DEAN, result.getStatus());
        assertNotNull(registration.getSubmittedAt());
        verify(eventPublisher).publishEvent(any(RegistrationSubmittedEvent.class));
    }

    @Test
    void onlyPendingDeanCanBeApproved() {
        registration.setStatus(RegistrationStatus.DRAFT);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.approveRegistration(dean, registration.getId(), null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(tuitionRepository, never()).save(any(TuitionAccount.class));
    }

    @Test
    void approvalCreatesTuitionAccount() {
        registration.setStatus(RegistrationStatus.PENDING_DEAN);
        when(feeStructureRepository.findBySemesterIdAndProgramId(semester.getId(), profile.getProgram().getId()))
                .thenReturn(Optional.of(FeeStructure.builder()
                        .costPerCreditUnit(new BigDecimal("100")).build()));
        when(adminChargeRepository.findBySemesterId(semester.getId())).thenReturn(List.of());
        when(creditBalanceRepository.findByStudentIdAndIsAppliedFalse(student.getId())).thenReturn(List.of());
        when(systemConfigService.getIntValue(SystemConfigKeys.MIN_PAYMENT_PERCENTAGE)).thenReturn(70);

        RegistrationResponse result = service.approveRegistration(dean, registration.getId(), null);

        assertEquals(RegistrationStatus.APPROVED, result.getStatus());
        verify(tuitionRepository).save(argThat(account ->
                account.getRegistration() == registration
                        && account.getMinimumPayment().compareTo(new BigDecimal("1400.00")) == 0));
    }

    @Test
    void tuitionAccountCreationFailurePropagatesFromTransactionalApproval() throws NoSuchMethodException {
        registration.setStatus(RegistrationStatus.PENDING_DEAN);
        when(feeStructureRepository.findBySemesterIdAndProgramId(semester.getId(), profile.getProgram().getId()))
                .thenReturn(Optional.of(FeeStructure.builder()
                        .costPerCreditUnit(new BigDecimal("100")).build()));
        when(adminChargeRepository.findBySemesterId(semester.getId())).thenReturn(List.of());
        when(creditBalanceRepository.findByStudentIdAndIsAppliedFalse(student.getId())).thenReturn(List.of());
        when(systemConfigService.getIntValue(SystemConfigKeys.MIN_PAYMENT_PERCENTAGE)).thenReturn(70);
        when(tuitionRepository.save(any(TuitionAccount.class)))
                .thenThrow(new IllegalStateException("tuition insert failed"));

        assertThrows(IllegalStateException.class,
                () -> service.approveRegistration(dean, registration.getId(), null));
        assertNotNull(RegistrationServiceImp.class
                .getMethod("approveRegistration", _User.class, UUID.class,
                        com.kad.jet.registration.dto.DeanReviewRequest.class)
                .getAnnotation(Transactional.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onlyPendingDeanCanBeRejected() {
        registration.setStatus(RegistrationStatus.APPROVED);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.rejectRegistration(dean, registration.getId(), review("No")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void rejectionReturnsTheSameRecordToDraftAndItCanBeResubmitted() {
        registration.setStatus(RegistrationStatus.PENDING_DEAN);

        RegistrationResponse rejected = service.rejectRegistration(dean, registration.getId(), review("Fix courses"));
        RegistrationResponse resubmitted = service.submitRegistration(
                student, registration.getId(), new SubmitRegistrationRequest());

        assertEquals(RegistrationStatus.DRAFT, rejected.getStatus());
        assertEquals(registration.getId(), rejected.getId());
        assertEquals(RegistrationStatus.PENDING_DEAN, resubmitted.getStatus());
        assertEquals(registration.getId(), resubmitted.getId());
        verify(eventPublisher).publishEvent(any(RegistrationRejectedEvent.class));
        verify(registrationRepository, times(2)).save(registration);
    }

    private DeanReviewRequest review(String remarks) {
        DeanReviewRequest request = new DeanReviewRequest();
        request.setRemarks(remarks);
        return request;
    }
}
