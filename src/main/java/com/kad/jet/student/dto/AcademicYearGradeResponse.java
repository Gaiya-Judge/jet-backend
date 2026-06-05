package com.kad.jet.student.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class AcademicYearGradeResponse {

    private String studentName;
    private String matricNumber;
    private String academicYear;

    private List<SemesterGradeGroup> semesters;
    // One entry per semester in this academic year
    // e.g. FIRST and SECOND
    // Each contains the grades and credit unit total
}
