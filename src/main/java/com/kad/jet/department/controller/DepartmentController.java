package com.kad.jet.department.controller;

import com.kad.jet.department.dto.*;
import com.kad.jet.department.service.DepartmentService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<?> createNewDepartment(@AuthenticationPrincipal _User currentUer,
                                                 @RequestBody @Valid CreateDepartmentRequest departmentRequest){
        DepartmentResponse response = departmentService.createDepartment(currentUer, departmentRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllDepartment(@RequestParam(defaultValue = "10") int size,
                                              @RequestParam(defaultValue = "0") int page){
        DepartmentPageResponse<DepartmentResponse> response = departmentService.getAllDepartment(size, page);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable(name = "id")UUID departmentId){
        DepartmentResponse response = departmentService.getDepartment(departmentId);

        return ResponseEntity.status(HttpStatus.FOUND)
                .body(response);
    }

    @GetMapping("/program/{id}")
    public ResponseEntity<?> getByDepartmentByProgram(@PathVariable(name = "id") UUID programId){
        List<DepartmentResponse> response = departmentService.getProgramDepartments(programId);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> softwareDeleteDepartment(@PathVariable(name = "id") UUID departmentId,
                                                      @AuthenticationPrincipal _User currentUser){
        departmentService.softDeleteDepartment(currentUser, departmentId);

        return ResponseEntity.ok()
                .body("department inactive");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDepartment(@PathVariable(name = "id") UUID departmentId,
                                              @AuthenticationPrincipal _User currentUser,
                                              @RequestBody UpdateDepartmentRequest departmentRequest){
        DepartmentResponse response = departmentService.updateDepartment(currentUser, departmentId, departmentRequest);

        return ResponseEntity.ok()
                .body(response);
    }

    @PutMapping("/{id}/hod")
    public ResponseEntity<?> assignHod(@AuthenticationPrincipal _User currentUser,
                                       @PathVariable(name = "id") UUID departmentId,
                                       @RequestBody @Valid AssignHodToDepartment assignHodToDepartment){
        DepartmentResponse response = departmentService.assignHod(currentUser, departmentId, assignHodToDepartment);

        return ResponseEntity.ok()
                .body(response);
    }

    @GetMapping("/admin")
    public ResponseEntity<?> getAllDepartment(@AuthenticationPrincipal _User currentUser,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(defaultValue = "0") int page){
        DepartmentPageResponse<DepartmentResponse> response = departmentService.adminGetAllDepartment(size, page, currentUser);

        return ResponseEntity.ok()
                .body(response);
    }

    @GetMapping("/program/{id}/admin")
    public ResponseEntity<?> getProgramDepartments(@AuthenticationPrincipal _User currentUser,
                                                   @PathVariable(name = "id")UUID programId){
        List<DepartmentResponse> response = departmentService.adminGetProgramDepartments(programId, currentUser);

        return ResponseEntity.ok()
                .body(response);
    }

    @GetMapping("/{id}/admin")
    public ResponseEntity<?> adminGetDepartment(@AuthenticationPrincipal _User currentUser,
                                                @PathVariable(name = "id") UUID departmentId){
        DepartmentResponse response = departmentService.adminGetDepartment(departmentId, currentUser);

        return ResponseEntity.status(HttpStatus.FOUND)
                .body(response);
    }
}
