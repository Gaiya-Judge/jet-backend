package com.kad.jet.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistrationItemResponse {

    private UUID registrationItemId;
    private UUID courseId;
    private String courseCode;
    private String courseTitle;
    private Integer creditUnits;
    private String courseType;
    private Boolean isCarryover;
    private Boolean isDroppable;
}
