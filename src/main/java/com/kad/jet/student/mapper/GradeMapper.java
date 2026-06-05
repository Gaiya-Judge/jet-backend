package com.kad.jet.student.mapper;

import com.kad.jet.student.dto.StudentGradeResponse;
import com.kad.jet.student.model.StudentGrade;

public class GradeMapper {

    public static StudentGradeResponse toDto(StudentGrade grade) {
        return StudentGradeResponse.builder()
                .id(grade.getId())
                .studentId(grade.getStudent().getId())
                .studentName(grade.getStudent().getFullName())
//                .matricNumber(grade.getStudentProfile()
//                        .getMatricNumber())
                .courseId(grade.getCourse().getId())
                .courseCode(grade.getCourse().getCourseCode())
                .courseTitle(grade.getCourse().getTitle())
                .semesterId(grade.getSemester().getId())
                .academicYear(grade.getSemester().getAcademicYear())
                .semesterType(grade.getSemester()
                        .getSemesterType().name())
                .grade(grade.getGrade())
                .createdAt(grade.getCreatedAt())
                .build();
    }
}
