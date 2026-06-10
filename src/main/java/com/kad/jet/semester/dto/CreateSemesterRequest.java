package com.kad.jet.semester.dto;

import com.kad.jet.semester.model.SemesterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateSemesterRequest {

    @NotBlank(message = "Academic year is required")
    private String academicYear;

    @NotNull(message = "semester type is required")
    private SemesterType semesterType;
}
