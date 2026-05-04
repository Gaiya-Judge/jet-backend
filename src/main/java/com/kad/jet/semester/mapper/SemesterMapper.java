package com.kad.jet.semester.mapper;

import com.kad.jet.semester.dto.SemesterResponse;
import com.kad.jet.semester.model.Semester;

public class SemesterMapper {

    public static SemesterResponse toDto(Semester semester){
        return SemesterResponse
                .builder()
                .id(semester.getId())
                .academicYear(semester.getAcademyYear())
                .addDropEnd(semester.getAddDropEnd())
                .semesterType(semester.getSemesterType())
                .addDropStart(semester.getAddDropStart())
                .status(semester.getStatus())
                .registrationOpen(semester.getRegistrationOpen())
                .createdAt(semester.getCreatedAt())
                .build();
    }
}
