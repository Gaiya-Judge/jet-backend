package com.kad.jet.user.service;

import com.kad.jet.security.repository.RefreshTokenRepository;
import com.kad.jet.security.service.JwtUtil;
import com.kad.jet.user.dto.*;
import com.kad.jet.user.mapper.UserMapper;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import com.kad.jet.user.utility.RoleChecker;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserAuthServiceImp implements UserAuthService{

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long jwtRefreshTokenExpiration;


    @Transactional
    @Override
    public AuthResponse login(LoginRequest loginRequest) {


        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );

        if (!authentication.isAuthenticated()){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad login credentials");
        }

        _User user = userRepository.findByEmailIgnoreCase(loginRequest.getEmail())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not recognized"));

        String accessToken = JwtUtil.generateAccessToken(user);
        String refreshToken = JwtUtil.generateRefreshToken(user);

//        refreshTokenRepository.deleteByUser_Id(user.getId());
//
//        RefreshToken newrefreshToken =
//                RefreshToken
//                        .builder()
//                        .user(user)
//                        .token(refreshToken)
//                        .createdAt(LocalDateTime.now())
//                        .expiresAt(JwtUtil.extractExpiration(refreshToken))
//                        .isRevoked(false)
//                        .build();
//
//        refreshTokenRepository.save(newrefreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getEmail(),
                user.getUserRole().name(),
                user.getId()
        );
    }


    @Transactional
    @Override
    public AuthResponse refreshToken(RefreshTokenRequest refreshTokenRequest) {
        return null;
    }

    @Transactional
    @Override
    public UserResponse userAccountCreation(_User currentUser, UsersAccountCreationRequest request) {
        //check if current user have permission
        if (!RoleChecker.hasAnyRole(currentUser, UserRole.IT_ADMIN, UserRole.SUPER_ADMIN)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "you are not allowed to perform this action");
        }
        //check if account already exists
        boolean userExists = userRepository.existsByEmail(request.getEmail());

        if (userExists){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user with email already exists");
        }
        //create account
        _User newUserAccount = _User
                .builder()
                .userRole(request.getUserRole())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .createdAt(LocalDateTime.now())
                .createdBy(currentUser)
                .isActive(true)
                .isBanned(false)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        _User saveNewUserAccount = userRepository.save(newUserAccount);

        //return response
        return UserMapper.toDto(saveNewUserAccount);
    }


}
