package com.kad.jet.user.service;

import com.kad.jet.user.dto.*;
import com.kad.jet.user.model._User;

public interface UserAuthService {

    AuthResponse login(LoginRequest loginRequest);

    AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest);

    UserResponse userAccountCreation(_User currentUser, UsersAccountCreationRequest request);


}
