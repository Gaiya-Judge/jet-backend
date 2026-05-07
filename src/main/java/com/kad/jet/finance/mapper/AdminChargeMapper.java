package com.kad.jet.finance.mapper;

import com.kad.jet.finance.dto.AdminChargeResponse;
import com.kad.jet.finance.model.AdminCharge;

public class AdminChargeMapper {

    public static AdminChargeResponse toDto(AdminCharge adminCharge){
        return AdminChargeResponse
                .builder()
                .id(adminCharge.getId())
                .academicYear(adminCharge.getSemester().getAcademyYear())
                .amount(adminCharge.getAmount())
                .createdAt(adminCharge.getCreatedAt())
                .name(adminCharge.getName())

                .semesterId(adminCharge.getSemester().getId())
                .semesterType(adminCharge.getSemester().getSemesterType().name())
                .build();
    }
}
