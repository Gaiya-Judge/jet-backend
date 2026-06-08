package com.kad.jet.user.controller;

import com.kad.jet.user.dto.AuthResponse;
import com.kad.jet.user.dto.ChangePasswordRequest;
import com.kad.jet.user.dto.LoginRequest;
import com.kad.jet.user.dto.RefreshTokenRequest;
import com.kad.jet.user.model._User;
import com.kad.jet.user.service.UserAuthService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/user/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request){
        AuthResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody @Valid RefreshTokenRequest request){
        AuthResponse response = authService.refreshToken(request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal _User currentUser,
                                            @RequestBody @Valid ChangePasswordRequest passwordRequest
                                            ){
        authService.changePassword(passwordRequest, currentUser);

        return ResponseEntity.ok("password changed");
    }


}
