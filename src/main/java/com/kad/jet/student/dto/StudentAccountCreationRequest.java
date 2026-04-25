package com.kad.jet.student.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StudentAccountCreationRequest {

    private String fullName;
    private String matricNumber;
    private Integer enrollmentYear;
    private Integer level;
    private String email;
    private String password;
}
