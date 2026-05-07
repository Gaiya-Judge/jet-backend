package com.kad.jet.student.service;

import com.kad.jet.student.dto.StudentAccountCreationRequest;
import com.kad.jet.student.dto.StudentProfileResponse;
import com.kad.jet.student.mapper.StudentProfileMapper;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.student.repository.StudentProfileRepository;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentProfileServiceImp implements StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

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
        //create new user

        _User newUser = _User
                .builder()
                .userRole(UserRole.STUDENT)
                .email(request.getEmail())
                .createdBy(currentUser)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .isActive(true)
                .isBanned(false)
                .createdAt(LocalDateTime.now())
                .build();

        _User savedNewUser = userRepository.save(newUser);

        StudentProfile newStudentProfile = StudentProfile
                .builder()
                .matricNumber(request.getMatricNumber())
                .user(savedNewUser)
                .level(request.getLevel())
                .enrollmentYear(request.getEnrollmentYear())
                .createdAt(LocalDateTime.now())
                .build();

        StudentProfile savedNewStudentProfile = studentProfileRepository.save(newStudentProfile);

        return StudentProfileMapper.toDto(savedNewStudentProfile);
    }
}
