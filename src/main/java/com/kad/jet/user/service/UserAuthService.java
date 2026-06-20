package com.kad.jet.user.service;

import com.kad.jet.user.dto.*;
import com.kad.jet.user.model._User;

import java.util.UUID;

public interface UserAuthService {

    AuthResponse login(LoginRequest loginRequest);

    AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest);

    UserResponse userAccountCreation(_User currentUser, UsersAccountCreationRequest request);

    void banUser(_User currentUser, UUID userId);

    UserResponse unbanUser(_User currentUser, UUID userId);

    void changePassword(ChangePasswordRequest passwordRequest, _User currentUser);

    UserResponse adminUpdateUserPassword(
            _User currentUser,
            UUID targetUserId,
            AdminResetPasswordRequest request);


}
