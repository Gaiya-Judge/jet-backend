package com.kad.jet.department.mapper;

import com.kad.jet.department.dto.DepartmentResponse;
import com.kad.jet.department.model.Department;

public class DepartmentMapper {

    public static DepartmentResponse toDto(Department department){
        return DepartmentResponse
                .builder()
                .department_name(department.getName())
                .createdAt(department.getCreatedAt())
                .departmentId(department.getId())
                .hod_id(department.getHod() != null ?
                        department.getHod().getId() : null)
                .is_active(department.getIsActive())
                .program_id(department.getProgram() != null ?
                        department.getProgram().getId() : null)
                .build();
    }
}
