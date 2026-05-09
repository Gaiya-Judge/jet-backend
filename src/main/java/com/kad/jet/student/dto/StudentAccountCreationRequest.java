package com.kad.jet.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StudentAccountCreationRequest {

    @NotBlank(message = "student name required")
    private String fullName;

    @NotBlank(message = "student matric number required")
    private String matricNumber;

    @NotNull(message = "student enrollment year required")
    private Integer enrollmentYear;

    @NotNull(message = "Program is required")
    private UUID programId;


    @NotNull(message = "student current level required")
    private Integer level;

    @NotBlank(message = "student email required")
    private String email;

    @NotBlank(message = "password required")
    private String password;

    @NotNull(message = "Department is required")
    private UUID departmentId;
}
