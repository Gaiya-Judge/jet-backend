package com.kad.jet.student.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GradeImportError {

    private Integer rowNumber;
    // Which CSV row failed — makes it easy for IT_ADMIN
    // to find and fix the problem in the CSV file

    private String matricNumber;
    private String courseCode;
    private String reason;
    // Why this row failed
    // e.g. "matric number JOS/UG/2021/999 not found"
    // e.g. "grade X is not valid. must be A, B, C, D, E, or F"
    // e.g. "duplicate: this grade already exists"

}
