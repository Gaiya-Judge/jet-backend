package com.kad.jet.semester.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSemesterRequest {

    @NotBlank(message = "academy year required")
    private String academyYear;
}
