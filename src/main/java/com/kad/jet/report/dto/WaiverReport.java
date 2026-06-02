package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class WaiverReport {


    private Integer totalWaivers;

    private BigDecimal totalReducedMinimumPayments;
    // Sum of all reducedMinimumPayment values
    // Shows the total threshold reductions granted
    // NOT a reduction in fees — students still owe full amounts

    private List<WaiverReportEntry> entries;
}
