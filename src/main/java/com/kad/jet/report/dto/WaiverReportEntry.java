package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WaiverReportEntry {

    private UUID waiverId;
    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private String academicYear;
    private String semesterType;
    private Integer allowedPaymentPercentage;
    private BigDecimal reducedMinimumPayment;
    private BigDecimal totalDue;
    // Always the full amount — waivers do not reduce this
    private String reason;
    private String approvedByName;
    private LocalDateTime approvedAt;
}
