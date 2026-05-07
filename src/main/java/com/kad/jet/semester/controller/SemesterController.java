package com.kad.jet.semester.controller;

import com.kad.jet.semester.dto.*;
import com.kad.jet.semester.service.SemesterService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("api/semester")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @PostMapping
    public ResponseEntity<?> createSemester(@RequestBody @Valid CreateSemesterRequest semesterRequest,
                                            @AuthenticationPrincipal _User currentUser){
        SemesterResponse response =
                semesterService.createSemester(currentUser, semesterRequest);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllSemesters(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                semesterService.getAllSemester(pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<SemesterResponse> getActiveSemester() {
        SemesterResponse response = semesterService.getActiveSemester();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSemesterById(
            @PathVariable UUID id) {
        SemesterResponse response = semesterService.getSemesterById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSemester(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSemesterRequest request,
            @AuthenticationPrincipal _User currentUser) {

        SemesterResponse response = semesterService.updateSemester(currentUser, id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<SemesterResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody SemesterStatusRequest request,
            @AuthenticationPrincipal _User currentUser) {

        SemesterResponse response = semesterService.updateStatus(currentUser, id, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/add-drop")
    public ResponseEntity<SemesterResponse> configureAddDrop(
            @PathVariable UUID id,
            @Valid @RequestBody AddDropRequest request,
            @AuthenticationPrincipal _User currentUser) {

        SemesterResponse response = semesterService.configureAddAndDrop(currentUser, id, request);
        return ResponseEntity.ok(response);
    }
}
