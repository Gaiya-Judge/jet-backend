package com.kad.jet.registration.mapper;

import com.kad.jet.registration.dto.RegistrationItemResponse;
import com.kad.jet.registration.dto.RegistrationResponse;
import com.kad.jet.registration.model.Registration;
import com.kad.jet.registration.model.RegistrationItem;

import java.util.List;

public class RegistrationMapper {

    public static RegistrationResponse toDto(Registration registration, String matricNumber, List<RegistrationItemResponse> courses) {

        return RegistrationResponse.builder()
                .id(registration.getId())
                .studentId(registration.getStudent().getId())
                .studentName(registration.getStudent().getFullName())
                .matricNumber(matricNumber)
                .semesterId(registration.getSemester().getId())
                .academicYear(registration.getSemester()
                        .getAcademyYear())
                .semesterType(registration.getSemester()
                        .getSemesterType().name())
                .status(registration.getStatus())
                .totalCreditUnits(registration.getTotalCreditUnits())
                .submittedAt(registration.getSubmittedAt())
                .deanReviewedAt(registration.getDeanReviewAt())
                .deanRemarks(registration.getDeanRemark())
                .assistedBy(registration.getAssistedBy() != null
                        ? registration.getAssistedBy().getId()
                        : null)
                .assistedByName(registration.getAssistedBy() != null
                        ? registration.getAssistedBy().getFullName()
                        : null)
                .assistReason(registration.getAssistReason())
                .createdAt(registration.getCreatedAt())
                .courses(courses)
                .build();
    }

    public static RegistrationItemResponse toItemDto(
            RegistrationItem item) {
        return RegistrationItemResponse.builder()
                .registrationItemId(item.getId())
                .courseId(item.getCourse().getId())
                .courseCode(item.getCourse().getCourseCode())
                .courseTitle(item.getCourse().getTitle())
                .creditUnits(item.getCourse().getCreditUnits())
                .courseType(item.getCourse().getCourseType().name())
                .isCarryover(item.getIsCarryOver())
                .isDroppable(item.getIsDroppable())
                .build();
    }
}
