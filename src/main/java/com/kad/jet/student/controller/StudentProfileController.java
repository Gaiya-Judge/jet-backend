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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/student")
@RequiredArgsConstructor
public class  StudentProfileController {

    private final StudentProfileService studentProfileService;

    @PostMapping("/create-account")
    public ResponseEntity<?> createStudentAccount(@AuthenticationPrincipal _User currentUser, @RequestBody @Valid StudentAccountCreationRequest creationRequest){
        StudentProfileResponse response = studentProfileService.createStudentAccount(currentUser, creationRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

}
