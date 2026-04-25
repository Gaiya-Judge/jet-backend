package com.kad.jet.student.mapper;

import com.kad.jet.student.dto.StudentProfileResponse;
import com.kad.jet.student.model.StudentProfile;
import com.kad.jet.user.dto.UserResponse;
import com.kad.jet.user.model._User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class StudentProfileMapper {

    public static StudentProfileResponse toDto(StudentProfile studentProfile){
        _User user = studentProfile.getUser();

        return StudentProfileResponse
                .builder()
                .studentId(studentProfile.getId())
                .level(studentProfile.getLevel())
                .createdAt(studentProfile.getCreatedAt())
                .enrollmentYear(studentProfile.getEnrollmentYear())
                .matricNumber(studentProfile.getMatricNumber())
                .userResponse(UserResponse
                        .builder()
                        .userRole(user.getUserRole().name())
                        .userId(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .build())
                .build();
    }
}
