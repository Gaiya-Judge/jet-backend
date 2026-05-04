package com.kad.jet.semester.dto;

import com.kad.jet.semester.model.SemesterStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SemesterStatusRequest {

    @NotNull(message = "Status is required")
    private SemesterStatus status;
}
