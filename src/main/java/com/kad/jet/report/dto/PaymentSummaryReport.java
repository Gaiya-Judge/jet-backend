package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PaymentSummaryReport {

    private String academicYear;
    private String semesterType;
    private Integer totalStudents;
    // Total students with tuition accounts this semester

    private BigDecimal totalExpected;
    // Sum of all total_due across all tuition accounts

    private BigDecimal totalCollected;
    // Sum of all total_paid across all tuition accounts

    private BigDecimal totalOutstanding;
    // totalExpected - totalCollected

    private BigDecimal totalWaivers;
    // Sum of all waiver amounts applied this semester

    private Integer fullyPaidCount;
    // Students who have paid 100%

    private Integer partiallyPaidCount;
    // Students who met the 70% minimum but not 100%

    private Integer unpaidCount;
    // Students who have not paid anything yet

    private List<StudentPaymentSummary> studentBreakdown;
}
