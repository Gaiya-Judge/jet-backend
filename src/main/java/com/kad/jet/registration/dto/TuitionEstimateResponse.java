package com.kad.jet.registration.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class TuitionEstimateResponse {

    private Integer totalCreditUnits;
    private BigDecimal costPerCreditUnit;
    private BigDecimal tuitionAmount;
    private BigDecimal adminChargesTotal;
    private Map<String, BigDecimal> adminChargesBreakdown;
    // e.g. {"Library Fee": 5000, "ICT Fee": 3000}
    private BigDecimal creditCarriedForward;
    private BigDecimal estimatedTotal;
    private BigDecimal minimumPayment;
    // 70% of estimatedTotal
}
