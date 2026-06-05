package com.kad.jet.report.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CarryoverStudentReport {

    private String academicYear;
    private String semesterType;
    private Integer totalStudentsWithCarryover;
    private List<CarryoverStudentEntry> entries;
}
