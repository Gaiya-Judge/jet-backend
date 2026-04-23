package com.kad.jet.user.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        String refreshToken,
        String email,
        String role,
        UUID userId
) {
}
