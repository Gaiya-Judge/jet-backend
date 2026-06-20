package com.kad.jet.user.controller;

import com.kad.jet.user.dto.AdminResetPasswordRequest;
import com.kad.jet.user.dto.UserResponse;
import com.kad.jet.user.dto.UsersAccountCreationRequest;
import com.kad.jet.user.model._User;
import com.kad.jet.user.service.UserAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PatchMapping("/{id}/ban")
    public ResponseEntity<?> banUser(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable(name = "id")UUID userId
            ){

        userAuthService.banUser(currentUser, userId);

        return ResponseEntity.ok(
                "user with id " + userId +
                "is ban"
        );
    }

    @PatchMapping("/{id}/unban")
    public ResponseEntity<?> unbanUser(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable(name = "id") UUID userId
    ){
        UserResponse response =
                userAuthService.unbanUser(currentUser, userId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/change-password")
    public ResponseEntity<?> adminUpdateUserPassword(
            @AuthenticationPrincipal _User currentUser,
            @PathVariable UUID id,
            @RequestBody @Valid AdminResetPasswordRequest request
    ){
        UserResponse response =
                userAuthService.adminUpdateUserPassword(currentUser, id, request);

        return ResponseEntity.ok(response);
    }

}
