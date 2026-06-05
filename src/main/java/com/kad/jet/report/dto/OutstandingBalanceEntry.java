package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OutstandingBalanceEntry {

    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private String programName;
    private String semesterAcademicYear;
    private String semesterType;
    private BigDecimal totalDue;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private Boolean isBanned;
    // Is the student currently banned from registering
}
