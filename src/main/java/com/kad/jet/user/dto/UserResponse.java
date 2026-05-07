package com.kad.jet.user.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.kad.jet.user.model.UserRole;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private String email;
    private UUID userId;
    private String fullName;
    private String userRole;
}
