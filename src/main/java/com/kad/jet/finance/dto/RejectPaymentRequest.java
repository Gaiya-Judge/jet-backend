package com.kad.jet.finance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectPaymentRequest {

    @NotBlank(message = "Reason is required")
    private String reason;
}
