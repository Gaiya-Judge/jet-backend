package com.kad.jet.student.dto;

import com.kad.jet.user.dto.UserResponse;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StudentProfileResponse {

    private String matricNumber;
    private Integer level;
    private Integer enrollmentYear;
    private UserResponse userResponse;
    private UUID studentId;
    private LocalDateTime createdAt;
    private String departmentName;
    private UUID departmentId;
    private String programName;
    private UUID programId;
}
