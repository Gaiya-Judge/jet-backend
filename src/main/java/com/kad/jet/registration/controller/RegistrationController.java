package com.kad.jet.registration.controller;

import com.kad.jet.registration.dto.*;
import com.kad.jet.registration.service.RegistrationService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    public ResponseEntity<?> createDraft(
            @AuthenticationPrincipal _User currentUser,
            @RequestBody(required = false) CreateRegistrationRequest request) {

        RegistrationResponse response =
                registrationService.createDraft(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{id}/course")
    public ResponseEntity<?> addCourse(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody AddCourseRequest request) {

        RegistrationResponse response =
                registrationService.addCourse(currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRegistrationById(@AuthenticationPrincipal _User currentUser,
                                                 @PathVariable UUID id) {

        RegistrationResponse response = registrationService
                        .getRegistrationById(currentUser, id);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyRegistrations(@AuthenticationPrincipal _User currentUser) {
        List<RegistrationResponse> response =
                registrationService.getMyRegistrations(currentUser);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getAllRegistrations(@AuthenticationPrincipal _User currentUser,
                                                 @PageableDefault(size = 10, sort = "createdAt",
                                                         direction = Sort.Direction.DESC) Pageable pageable) {
        Page<RegistrationResponse> response = registrationService
                .getAllRegistrations(currentUser, pageable);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/bulk")
    public ResponseEntity<?> addBulkCourses(@AuthenticationPrincipal _User currentUser,
                                            @PathVariable(name = "id") UUID registrationId,
                                            @RequestBody BulkAddCourseRequest request){
        BulkAddCourseResponse response = registrationService.addCourses(currentUser, registrationId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRegistrations(
            @AuthenticationPrincipal _User currentUser,
            @PageableDefault(size = 10, sort = "submittedAt",
                    direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<RegistrationResponse> response =
                registrationService
                        .getPendingRegistrations(currentUser, pageable);

        return ResponseEntity.ok(response);
    }


    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitRegistration(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @RequestBody(required = false) SubmitRegistrationRequest request) {

        RegistrationResponse response =
                registrationService
                        .submitRegistration(currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/courses/{courseId}")
    public ResponseEntity<?> removeCourse(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @PathVariable UUID courseId) {

        RegistrationResponse response =
                registrationService
                        .removeCourse(currentUser, id, courseId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/courses")
    public ResponseEntity<?> getRegistrationCourses(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id) {

        List<RegistrationItemResponse> response =
                registrationService
                        .getRegistrationCourses(currentUser, id);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveRegistration(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @RequestBody(required = false) DeanReviewRequest request) {

        RegistrationResponse response =
                registrationService
                        .approveRegistration(currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectRegistration(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @RequestBody DeanReviewRequest request) {

        RegistrationResponse response =
                registrationService
                        .rejectRegistration(currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/tuition-estimate")
    public ResponseEntity<?> getTuitionEstimate(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id) {

        TuitionEstimateResponse response =
                registrationService
                        .getTuitionEstimate(currentUser, id);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/add-drop/add")
    public ResponseEntity<?> addCourseDuringAddDrop(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody AddDropCourseRequest request) {

        AddDropResponse response =
                registrationService.addCourseDuringAddDrop(
                        currentUser, id, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/add-drop/drop/{courseId}")
    public ResponseEntity<?> dropCourseDuringAddDrop(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @PathVariable UUID courseId,
            @RequestParam(required = false) String assistReason) {

        AddDropResponse response =
                registrationService.dropCourseDuringAddDrop(
                        currentUser, id, courseId, assistReason);

        return ResponseEntity.ok(response);
    }
}
