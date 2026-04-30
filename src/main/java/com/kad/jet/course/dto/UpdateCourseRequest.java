package com.kad.jet.course.dto;

import com.kad.jet.course.model.CourseType;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateCourseRequest {

    private String title;

    @Min(value = 1, message = "Credit units must be at least 1")
    private Integer creditUnits;

    private CourseType courseType;

    private Integer level;
}
