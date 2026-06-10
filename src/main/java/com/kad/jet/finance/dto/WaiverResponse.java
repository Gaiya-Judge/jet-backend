package com.kad.jet.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WaiverResponse {

    private UUID id;
    private UUID tuitionAccountId;
    private String studentName;
    private String matricNumber;
    private Integer allowedPaymentPercentage;
    // e.g. 40
    private BigDecimal reducedMinimumPayment;
    // e.g. ₦56,400
    private BigDecimal originalMinimumPayment;
    // e.g. ₦98,700 — what it would have been without waiver
    private BigDecimal totalDue;
    // Unchanged — student still owes this full amount
    private String reason;
    private String approvedByName;
    private LocalDateTime approvedAt;
}
