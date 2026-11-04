package com.kad.jet.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResendActivationRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;
}
