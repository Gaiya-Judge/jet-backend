package com.kad.jet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateFeeStructureRequest {

    @NotNull(message = "Cost per credit unit is required")
    @DecimalMin(value = "0.1", message = "Cost per credit unit must be greater than zero")
    private BigDecimal costPerCreditUnit;
}
