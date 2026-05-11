package com.kad.jet.registration.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BulkAddCourseResponse {

    private List<UUID> added;
    private List<FailedCourse> failed;
    private Integer totalCreditUnits;
    private RegistrationResponse registration;

    @Data
    @Builder
    public static class FailedCourse {
        private UUID courseId;
        private String courseCode;
        private String reason;
    }
}
