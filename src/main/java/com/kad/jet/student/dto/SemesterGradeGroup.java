package com.kad.jet.student.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SemesterGradeGroup {

    private String academicYear;
    // e.g. "2024/2025"

    private String semesterType;
    // e.g. "FIRST", "SECOND", "SUMMER"

    private Integer totalCreditUnits;
    // Sum of credit units for all courses in this semester

    private List<StudentGradeResponse> grades;
    // Individual course grades for this semester
}
