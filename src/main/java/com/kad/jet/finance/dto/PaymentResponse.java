package com.kad.jet.finance.dto;

import com.kad.jet.finance.model.PaymentMethod;
import com.kad.jet.finance.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {

    private UUID id;
    private UUID tuitionAccountId;
    private String idempotencyKey;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String paystackReference;
    private String tellerImageUrl;
    private UUID verifiedBy;
    private String verifiedByName;
    private LocalDateTime verifiedAt;
    private LocalDateTime initiatedAt;
    private LocalDateTime createdAt;
}
