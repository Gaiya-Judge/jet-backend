package com.kad.jet.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class RefreshTokenRequest {

    @NotBlank(message = "refresh token is required")
    private String refreshToken;
}
