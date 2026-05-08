package com.kad.jet.course.dto;

import com.kad.jet.course.model.CourseType;
import com.kad.jet.semester.model.SemesterType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCourseRequest {

    @NotBlank(message = "Course code is required")
    private String courseCode;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Credit units is required")
    @Min(value = 1, message = "Credit units must be at least 1")
    private Integer creditUnits;

    @NotNull(message = "Course type is required")
    private CourseType courseType;

    @NotNull(message = "Semester type is required")
    private SemesterType semesterType;

    @NotNull(message = "Department is required")
    private UUID departmentId;

    @NotNull(message = "Level is required")
    private Integer level;
}
