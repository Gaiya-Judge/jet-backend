package com.kad.jet.registration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddDropCourseRequest {

    @NotNull(message = "course id is required")
    private UUID courseId;

    private String assistReason;
    // Required when IT_ADMIN is performing the action\
}
