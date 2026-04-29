package com.kad.jet.department.service;

import com.kad.jet.department.dto.*;
import com.kad.jet.user.model._User;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface DepartmentService {

    DepartmentResponse createDepartment(_User currentUser, CreateDepartmentRequest departmentRequest);

    void softDeleteDepartment(_User currentUser, UUID departmentId);

    DepartmentResponse updateDepartment(_User currentUser, UUID departmentId, UpdateDepartmentRequest departmentRequest);

    DepartmentResponse getDepartment(UUID departmentIid);

    DepartmentResponse adminGetDepartment(UUID departmentId, _User currentUser);

    DepartmentPageResponse<DepartmentResponse> adminGetAllDepartment(int size, int page, _User currentUSer);

    DepartmentPageResponse<DepartmentResponse> getAllDepartment(int size, int page);

    List<DepartmentResponse> getProgramDepartments(UUID programId);

    List<DepartmentResponse> adminGetProgramDepartments(UUID programId, _User currentUser);

    DepartmentResponse assignHod(_User currentUser, UUID departmentId, AssignHodToDepartment assignHodToDepartment);

    DepartmentResponse restoreSoftDeletedDepartment(_User currentUser, UUID departmentId);
}
