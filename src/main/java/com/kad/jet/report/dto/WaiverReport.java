package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class WaiverReport {

    private Integer totalWaivers;
    private BigDecimal totalWaiverAmount;
    private List<WaiverReportEntry> entries;
}
