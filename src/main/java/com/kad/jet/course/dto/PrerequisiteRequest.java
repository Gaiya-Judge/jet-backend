package com.kad.jet.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class PrerequisiteRequest {

    @NotNull(message = "Required course id is required")
    private UUID requiredCourseId;
}
