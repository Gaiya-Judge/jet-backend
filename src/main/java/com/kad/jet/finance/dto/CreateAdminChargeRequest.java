package com.kad.jet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateAdminChargeRequest {

    @NotNull(message = "Semester is required")
    private UUID semesterId;

    @NotBlank(message = "Charge name is required")
    private String name;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.1",
            message = "Amount must be greater than zero")
    private BigDecimal amount;
}
