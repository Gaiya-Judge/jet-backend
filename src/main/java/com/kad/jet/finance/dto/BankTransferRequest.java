package com.kad.jet.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class BankTransferRequest {

    @NotNull(message = "Tuition account id is required")
    private UUID tuitionAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.1",
            message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Teller number is required")
    private String tellerNumber;

    @NotBlank(message = "Teller image URL is required")
    private String tellerImageUrl;
}
