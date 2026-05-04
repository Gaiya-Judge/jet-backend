package com.kad.jet.semester.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kad.jet.semester.model.SemesterStatus;
import com.kad.jet.semester.model.SemesterType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SemesterResponse {

    private UUID id;
    private String academicYear;
    private SemesterType semesterType;
    private SemesterStatus status;
    private LocalDate addDropStart;
    private LocalDate addDropEnd;
    private LocalDate registrationOpen;
    private LocalDateTime createdAt;
}
