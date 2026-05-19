package com.kad.jet.finance.dto;

import com.kad.jet.finance.model.WaiverType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateWaiverRequest {


    @NotNull(message = "tuition account id is required")
    private UUID tuitionAccountId;

    @NotNull(message = "waiver type is required")
    private WaiverType waiverType;
    // PERCENTAGE — e.g. 20 means 20% off total due
    // FIXED      — e.g. 10000 means ₦10,000 off total due

    @NotNull(message = "value is required")
    @DecimalMin(value = "0.01",
            message = "waiver value must be greater than zero")
    private BigDecimal value;

    @NotBlank(message = "reason is required — " +
            "waivers must have a written justification")
    private String reason;

}
