package com.kad.jet.user.dto;

import com.kad.jet.user.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UsersAccountCreationRequest {

    //@NotBlank(message = "user role is required")
    @NotNull
    UserRole userRole;

    @NotBlank(message = "user email required")
    String email;

    @NotBlank(message = "insert user full name")
    String fullName;

    @NotBlank(message = "password needed for user authentication")
    String password;

}
