package com.kad.jet.registration.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RegistrationItemResponse {

    private UUID id;
    private UUID courseId;
    private String courseCode;
    private String courseTitle;
    private Integer creditUnits;
    private String courseType;
    private Boolean isCarryover;
    private Boolean isDroppable;
}
