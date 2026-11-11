package com.kad.jet.student.service;

import com.kad.jet.department.model.Department;
import com.kad.jet.department.repository.DepartmentRepository;
import com.kad.jet.notification.event.AccountCreatedEvent;
import com.kad.jet.program.model.Program;
import com.kad.jet.program.repository.ProgramRepository;
import com.kad.jet.student.dto.StudentAccountCreationRequest;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import com.kad.jet.user.service.AccountActivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentProfileServiceImpTest {

    @Mock private StudentProfileRepository studentProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProgramRepository programRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountActivationService accountActivationService;

    private StudentProfileServiceImp service;

    @BeforeEach
    void setUp() {
        service = new StudentProfileServiceImp(
                studentProfileRepository,
                userRepository,
                programRepository,
                departmentRepository,
                eventPublisher,
                accountActivationService);
    }

    @Test
    void studentAccountCreationPersistsLinkedUserAndProfile() {
        UUID actorId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        _User actor = _User.builder()
                .id(actorId)
                .email("it@example.test")
                .userRole(UserRole.IT_ADMIN)
                .build();
        Program program = Program.builder().id(programId).name("Engineering").build();
        Department department = Department.builder()
                .id(departmentId)
                .name("Computer Engineering")
                .program(program)
                .build();
        StudentAccountCreationRequest request = new StudentAccountCreationRequest(
                "Student Name", "MAT-001", 2025, programId,
                100, "student@example.test", departmentId);

        when(studentProfileRepository.findByMatricNumber("MAT-001"))
                .thenReturn(java.util.Optional.empty());
        when(programRepository.findById(programId)).thenReturn(java.util.Optional.of(program));
        when(departmentRepository.findById(departmentId)).thenReturn(java.util.Optional.of(department));
        when(accountActivationService.createPendingPasswordHash()).thenReturn("unusable-hash");
        when(userRepository.save(any(_User.class))).thenAnswer(invocation -> {
            _User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(invocation -> {
            StudentProfile profile = invocation.getArgument(0);
            profile.setId(profile.getUser().getId());
            return profile;
        });

        var response = service.createStudentAccount(actor, request);

        ArgumentCaptor<_User> userCaptor = ArgumentCaptor.forClass(_User.class);
        verify(userRepository).save(userCaptor.capture());
        _User savedUser = userCaptor.getValue();
        assertEquals(UserRole.STUDENT, savedUser.getUserRole());
        assertEquals("unusable-hash", savedUser.getPassword());
        assertFalse(savedUser.getIsActive());
        assertEquals("student@example.test", savedUser.getEmail());

        ArgumentCaptor<StudentProfile> profileCaptor = ArgumentCaptor.forClass(StudentProfile.class);
        verify(studentProfileRepository).save(profileCaptor.capture());
        StudentProfile savedProfile = profileCaptor.getValue();
        assertSame(savedUser, savedProfile.getUser());
        assertEquals("MAT-001", savedProfile.getMatricNumber());
        assertSame(program, savedProfile.getProgram());
        assertSame(department, savedProfile.getDepartment());
        assertEquals(savedUser.getId(), response.getStudentId());
        assertEquals(savedUser.getId(), response.getUserResponse().getUserId());

        verify(eventPublisher).publishEvent(any(AccountCreatedEvent.class));
        verify(accountActivationService).issueInitialActivation(savedUser);
    }

    @Test
    void studentCannotReadAnotherStudentsProfile() {
        _User student = _User.builder()
                .id(UUID.randomUUID())
                .email("student@example.test")
                .userRole(UserRole.STUDENT)
                .build();
        UUID anotherStudentId = UUID.randomUUID();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getStudentProfileById(anotherStudentId, student));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(studentProfileRepository);
    }

    @Test
    void adminCanSearchStudentDirectoryAndReceivesSafeDto() {
        _User admin = _User.builder().id(UUID.randomUUID()).userRole(UserRole.IT_ADMIN).build();
        UUID studentId = UUID.randomUUID();
        Program program = Program.builder().id(UUID.randomUUID()).name("Theology").build();
        Department department = Department.builder().id(UUID.randomUUID()).name("Biblical Studies").build();
        _User student = _User.builder().id(studentId).email("person@example.test")
                .fullName("Example Person").userRole(UserRole.STUDENT).password("secret-hash").build();
        StudentProfile profile = StudentProfile.builder().id(studentId).user(student)
                .matricNumber("221041").level(200).enrollmentYear(2022)
                .program(program).department(department).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(studentProfileRepository.searchDirectory("221041", pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(profile), pageable, 1));

        var result = service.searchStudents(" 221041 ", pageable, admin);

        assertEquals(1, result.getTotalElements());
        assertEquals("221041", result.getContent().getFirst().getMatricNumber());
        assertEquals("Example Person", result.getContent().getFirst().getUserResponse().getFullName());
        assertFalse(result.getContent().getFirst().toString().contains("secret-hash"));
        verify(studentProfileRepository).searchDirectory("221041", pageable);
    }

    @Test
    void directoryRejectsNonAdminRoles() {
        for (UserRole role : UserRole.values()) {
            if (role == UserRole.IT_ADMIN || role == UserRole.SUPER_ADMIN) continue;
            _User user = _User.builder().userRole(role).build();
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> service.searchStudents(null, PageRequest.of(0, 10), user));
            assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        }
        verifyNoInteractions(studentProfileRepository);
    }

    @Test
    void blankDirectorySearchIsNormalizedToNoFilter() {
        _User admin = _User.builder().userRole(UserRole.SUPER_ADMIN).build();
        PageRequest pageable = PageRequest.of(1, 5);
        when(studentProfileRepository.searchDirectory(null, pageable)).thenReturn(Page.empty(pageable));

        var result = service.searchStudents("  ", pageable, admin);

        assertEquals(1, result.getNumber());
        assertEquals(5, result.getSize());
        verify(studentProfileRepository).searchDirectory(null, pageable);
    }
}
