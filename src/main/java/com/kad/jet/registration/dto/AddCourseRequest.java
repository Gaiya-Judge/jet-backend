package com.kad.jet.registration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddCourseRequest {

    @NotNull(message = "Course id is required")
    private UUID courseId;

    private String assistReason;
}
