package com.kad.jet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateAdminChargeRequest {

    private String name;

    @DecimalMin(value = "0.1",
            message = "Amount must be greater than zero")
    private BigDecimal amount;
}
