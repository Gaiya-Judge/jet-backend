package com.kad.jet.user.controller;

import com.kad.jet.user.dto.UserResponse;
import com.kad.jet.user.dto.UsersAccountCreationRequest;
import com.kad.jet.user.model._User;
import com.kad.jet.user.service.UserAuthService;
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
@RequestMapping("api/user")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAuthService userAuthService;

    @PostMapping("/create-account")
    public ResponseEntity<?> createUserAccount(@AuthenticationPrincipal _User currentUser, @RequestBody @Valid UsersAccountCreationRequest request){
        UserResponse response = userAuthService.userAccountCreation(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }
}
