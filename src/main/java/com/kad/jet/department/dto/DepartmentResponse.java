package com.kad.jet.department.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DepartmentResponse {

    private String department_name;
    private UUID hod_id;
    private UUID departmentId;
    private UUID program_id;
    private Boolean is_active;
    private LocalDateTime createdAt;

}
