package com.kad.jet.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RegistrationSummaryReport {

    private String academicYear;
    private String semesterType;
    private Integer totalRegistrations;
    private Integer draftCount;
    private Integer pendingDeanCount;
    private Integer approvedCount;
    private Integer rejectedCount;
    private Integer completedCount;
    private List<RegistrationSummaryEntry> entries;
}
