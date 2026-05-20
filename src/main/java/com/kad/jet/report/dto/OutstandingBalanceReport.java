package com.kad.jet.report.dto;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class OutstandingBalanceReport {

    private Integer totalStudentsWithBalance;
    private BigDecimal totalOutstandingAmount;
    private List<OutstandingBalanceEntry> entries;
}
