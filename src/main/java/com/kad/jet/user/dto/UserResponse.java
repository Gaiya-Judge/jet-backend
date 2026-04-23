package com.kad.jet.user.dto;


import com.kad.jet.user.model.UserRole;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserResponse {

    private String email;
    private UUID userId;
    private String fullName;
    private String userRole;
}
