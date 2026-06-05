package com.kad.jet.finance.mapper;

import com.kad.jet.finance.dto.FeeStructureResponse;
import com.kad.jet.finance.model.FeeStructure;

public class FeeStructureMapper {

    public static FeeStructureResponse toDto(FeeStructure feeStructure){
        return FeeStructureResponse
                .builder()
                .id(feeStructure.getId())
                .academicYear(feeStructure.getSemester().getAcademicYear())
                .costPerCreditUnit(feeStructure.getCostPerCreditUnit())
                .createdAt(feeStructure.getCreatedAt())
                .programId(feeStructure.getProgram().getId())
                .programName(feeStructure.getProgram().getName())
                .semesterId(feeStructure.getSemester().getId())
                .semesterType(feeStructure.getSemester().getSemesterType().name())
                .build();
    }
}
