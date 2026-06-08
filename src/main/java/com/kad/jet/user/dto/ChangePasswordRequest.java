package com.kad.jet.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank(message = "old password is required")
    private String oldPassword;

    @NotBlank(message = "new password is required")
    private String newPassword;
}
