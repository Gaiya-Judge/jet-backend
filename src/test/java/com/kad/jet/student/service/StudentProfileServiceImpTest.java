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

import java.util.UUID;

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
}
