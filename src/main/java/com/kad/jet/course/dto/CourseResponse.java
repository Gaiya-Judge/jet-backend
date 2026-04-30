package com.kad.jet.course.dto;

import com.kad.jet.course.model.CourseType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CourseResponse {

    private UUID id;
    private String courseCode;
    private String title;
    private Integer creditUnits;
    private CourseType courseType;
    private UUID departmentId;
    private String departmentName;
    private Integer level;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
