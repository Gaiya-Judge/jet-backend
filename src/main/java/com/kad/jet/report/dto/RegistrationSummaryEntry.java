package com.kad.jet.report.dto;

import com.kad.jet.registration.model.RegistrationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RegistrationSummaryEntry {

    private UUID registrationId;
    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private String programName;
    private Integer totalCreditUnits;
    private RegistrationStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime deanReviewedAt;
    private String deanRemarks;
}
