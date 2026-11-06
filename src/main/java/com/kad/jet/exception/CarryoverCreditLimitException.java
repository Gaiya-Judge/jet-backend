package com.kad.jet.exception;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class CarryoverCreditLimitException extends RuntimeException {

    private final int maximumCreditUnits;
    private final int totalCarryoverCreditUnits;
    private final List<CourseDetail> courses;

    public CarryoverCreditLimitException(int maximumCreditUnits,
                                         int totalCarryoverCreditUnits,
                                         List<CourseDetail> courses) {
        super("Compulsory carryover courses total " + totalCarryoverCreditUnits
                + " credit units, exceeding the maximum of "
                + maximumCreditUnits + ". Please contact the appropriate "
                + "academic administrator for resolution.");
        this.maximumCreditUnits = maximumCreditUnits;
        this.totalCarryoverCreditUnits = totalCarryoverCreditUnits;
        this.courses = List.copyOf(courses);
    }

    @Getter
    public static class CourseDetail {
        private final UUID courseId;
        private final String courseCode;
        private final String courseTitle;
        private final int creditUnits;

        public CourseDetail(UUID courseId, String courseCode,
                            String courseTitle, int creditUnits) {
            this.courseId = courseId;
            this.courseCode = courseCode;
            this.courseTitle = courseTitle;
            this.creditUnits = creditUnits;
        }
    }
}
