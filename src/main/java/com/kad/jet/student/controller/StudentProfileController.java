package com.kad.jet.student.controller;

import com.kad.jet.student.dto.StudentAccountCreationRequest;
import com.kad.jet.student.dto.StudentProfileResponse;
import com.kad.jet.student.service.StudentProfileService;
import com.kad.jet.user.model._User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("api/student")
@RequiredArgsConstructor
public class  StudentProfileController {

    private final StudentProfileService studentProfileService;

    @GetMapping
    public ResponseEntity<Page<StudentProfileResponse>> searchStudents(
            @AuthenticationPrincipal _User currentUser,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "matricNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(studentProfileService.searchStudents(search, pageable, currentUser));
    }

    @PostMapping("/create-account")
    public ResponseEntity<?> createStudentAccount(@AuthenticationPrincipal _User currentUser, @RequestBody @Valid StudentAccountCreationRequest creationRequest){
        StudentProfileResponse response = studentProfileService.createStudentAccount(currentUser, creationRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStudentProfile(@AuthenticationPrincipal _User currentUser, @PathVariable UUID id){
        StudentProfileResponse response =
                studentProfileService
                        .getStudentProfileById(id, currentUser);
        return ResponseEntity.ok(response);
    }

}
