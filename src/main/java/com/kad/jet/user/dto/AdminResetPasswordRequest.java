package com.kad.jet.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminResetPasswordRequest {

    @NotBlank(message = "new password is required")
    private String newPassword;

    @NotBlank(message = "reset reason is required")
    private String reason;
}
