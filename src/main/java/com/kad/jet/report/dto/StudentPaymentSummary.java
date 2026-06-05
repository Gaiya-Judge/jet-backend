package com.kad.jet.report.dto;

import com.kad.jet.finance.model.TuitionAccountStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StudentPaymentSummary {

    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private String programName;
    private BigDecimal totalDue;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private TuitionAccountStatus status;
}
