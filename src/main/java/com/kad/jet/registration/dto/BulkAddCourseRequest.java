package com.kad.jet.registration.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BulkAddCourseRequest {

    @NotEmpty(message = "At least one course is required")
    private List<UUID> courseIds;

    private String assistReason;
}
