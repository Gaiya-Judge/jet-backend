package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CarryoverStudentEntry {

    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private String programName;
    private Integer carryoverCourseCount;
    private Integer carryoverCreditUnits;
    private List<String> carryoverCourseCodes;
}
