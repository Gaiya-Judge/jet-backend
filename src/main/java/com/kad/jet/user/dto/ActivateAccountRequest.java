package com.kad.jet.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ActivateAccountRequest {

    @NotBlank(message = "activation token is required")
    @Size(max = 128, message = "activation token is invalid")
    private String token;

    @NotBlank(message = "password is required")
    private String password;

    @NotBlank(message = "password confirmation is required")
    private String passwordConfirmation;
}
