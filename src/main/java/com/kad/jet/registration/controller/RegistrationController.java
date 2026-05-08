package com.kad.jet.registration.controller;

import com.kad.jet.registration.dto.AddCourseRequest;
import com.kad.jet.registration.dto.CreateRegistrationRequest;
import com.kad.jet.registration.dto.RegistrationResponse;
import com.kad.jet.registration.service.RegistrationService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

        System.out.println("inside the controller");
        RegistrationResponse response =
                registrationService.createDraft(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{id}/courses")
    public ResponseEntity<?> addCourse(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody AddCourseRequest request) {

        RegistrationResponse response =
                registrationService.addCourse(currentUser, id, request);

        return ResponseEntity.ok(response);
    }
}
