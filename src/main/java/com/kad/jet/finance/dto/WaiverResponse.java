package com.kad.jet.finance.dto;

import com.kad.jet.finance.model.WaiverType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class WaiverResponse {

    private UUID id;
    private UUID tuitionAccountId;
    private WaiverType waiverType;
    private BigDecimal value;
    // The raw value — 20 for PERCENTAGE, 10000 for FIXED

    private BigDecimal computedAmount;
    // The actual Naira amount deducted from total due
    // For PERCENTAGE: computedAmount = total_due × (value/100)
    // For FIXED: computedAmount = value

    private String reason;
    private UUID approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
}
