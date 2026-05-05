package com.kad.jet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateFeeStructureRequest {

    @NotNull(message = "Semester is required")
    private UUID semesterId;

    @NotNull(message = "Program is required")
    private UUID programId;

    @NotNull(message = "Cost per credit unit is required")
    @DecimalMin(value = "0.1", message = "Cost per credit unit must be greater than zero")
    private BigDecimal costPerCreditUnit;
}
