package com.kad.jet.finance.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class AdminChargeResponse {

    private UUID id;
    private UUID semesterId;
    private String academicYear;
    private String semesterType;
    private String name;
    private BigDecimal amount;
    private LocalDateTime createdAt;

}
