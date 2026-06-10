package com.kad.jet.finance.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateWaiverRequest {


    @NotNull(message = "tuition account id is required")
    private UUID tuitionAccountId;

    @NotNull(message = "allowed payment percentage is required")
    @Min(value = 1,
            message = "percentage must be at least 1%")
    @Max(value = 99,
            message = "percentage must be less than 100%. " +
                    "use full payment if 100% is intended")
    private Integer allowedPaymentPercentage;
    // The reduced percentage the student is allowed to pay
    // e.g. 40 means they only need to pay 40% upfront
    // Must be lower than the system default (70%)
    // Bursar can technically set it higher but that
    // makes no sense so we validate below in service

    @NotBlank(message = "reason is required")
    private String reason;

}
