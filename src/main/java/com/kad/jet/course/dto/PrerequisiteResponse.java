package com.kad.jet.course.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PrerequisiteResponse {

    private UUID courseId;
    private UUID requiredCourseId;
    private String requiredCourseCode;
    private String requiredCourseTitle;
    private Integer requiredCreditUnits;
}
