package com.kad.jet.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FeeStructureResponse {

    private UUID id;
    private UUID semesterId;
    private String academicYear;
    private String semesterType;
    private UUID programId;
    private String programName;
    private BigDecimal costPerCreditUnit;
    private LocalDateTime createdAt;
}
