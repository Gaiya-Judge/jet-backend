package com.kad.jet.student.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class StudentGradeResponse {

    private UUID id;
    private UUID studentId;
    private String studentName;
    private String matricNumber;
    private UUID courseId;
    private String courseCode;
    private String courseTitle;
    private UUID semesterId;
    private String academicYear;
    private String semesterType;
    private String grade;
    private LocalDateTime createdAt;
}
