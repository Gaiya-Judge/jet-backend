package com.kad.jet.student.service;

import com.kad.jet.department.model.Department;
import com.kad.jet.department.repository.DepartmentRepository;
import com.kad.jet.notification.event.AccountCreatedEvent;
import com.kad.jet.program.model.Program;
import com.kad.jet.program.repository.ProgramRepository;
import com.kad.jet.student.dto.StudentAccountCreationRequest;
import com.kad.jet.student.dto.StudentProfileResponse;
import com.kad.jet.student.mapper.StudentProfileMapper;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import com.kad.jet.user.service.AccountActivationService;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileServiceImp implements StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountActivationService accountActivationService;

    @Transactional
    @Override
    public StudentProfileResponse createStudentAccount(_User currentUser, StudentAccountCreationRequest request) {
        //check role
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "you are not allowed to perform this action");
        }
        //check if student already exits
        Optional<StudentProfile> studentProfile = studentProfileRepository.findByMatricNumber(request.getMatricNumber());

        if (studentProfile.isPresent()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "student already exits");
        }

        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "program not found for id " +
                        request.getProgramId()));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "department not found for id " +
                        request.getDepartmentId()));

        if (!department.getProgram().getId().equals(program.getId())){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "department does not belong to the selected program");
        }

        //create new user

        _User newUser = _User
                .builder()
                .userRole(UserRole.STUDENT)
                .email(request.getEmail())
                .createdBy(currentUser)
                .password(accountActivationService.createPendingPasswordHash())
                .fullName(request.getFullName())
                .isActive(false)
                .isBanned(false)
                .createdAt(LocalDateTime.now())
                .build();

        _User savedNewUser = userRepository.save(newUser);
        accountActivationService.issueInitialActivation(savedNewUser);

        StudentProfile newStudentProfile = StudentProfile
                .builder()
                .matricNumber(request.getMatricNumber())
                .user(savedNewUser)
                .level(request.getLevel())
                .program(program)
                .department(department)
                .enrollmentYear(request.getEnrollmentYear())
                .createdAt(LocalDateTime.now())
                .build();

        StudentProfile savedNewStudentProfile = studentProfileRepository.save(newStudentProfile);

        eventPublisher.publishEvent(
                new AccountCreatedEvent(
                        savedNewUser.getId(),
                        currentUser.getId()));

        return StudentProfileMapper.toDto(savedNewStudentProfile);
    }

    @Override
    public StudentProfileResponse getStudentProfileById(UUID studentId, _User currentUser) {

        if (RoleChecker.is_student(currentUser) && !currentUser.getId().equals(studentId)){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "you can only view your own profile");
        }

        StudentProfile studentProfile = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "student profile not found"));

        return StudentProfileMapper.toDto(studentProfile);
    }
}
