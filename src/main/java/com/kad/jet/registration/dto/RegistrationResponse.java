package com.kad.jet.registration.dto;

import com.kad.jet.registration.model.RegistrationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RegistrationResponse {

    private UUID id;
    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private UUID semesterId;
    private String academicYear;
    private String semesterType;
    private RegistrationStatus status;
    private Integer totalCreditUnits;
    private LocalDateTime submittedAt;
    private LocalDateTime deanReviewedAt;
    private String deanRemarks;
    private UUID assistedBy;
    private String assistedByName;
    private String assistReason;
    private LocalDateTime createdAt;
    private List<RegistrationItemResponse> courses;
}
