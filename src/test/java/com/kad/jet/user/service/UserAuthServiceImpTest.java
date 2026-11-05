package com.kad.jet.user.service;

import com.kad.jet.security.model.RefreshToken;
import com.kad.jet.security.repository.RefreshTokenRepository;
import com.kad.jet.security.service.JwtUtil;
import com.kad.jet.user.dto.AuthResponse;
import com.kad.jet.user.dto.RefreshTokenRequest;
import com.kad.jet.user.dto.UsersAccountCreationRequest;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import com.kad.jet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceImpTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountActivationService accountActivationService;

    private UserAuthServiceImp service;

    @BeforeEach
    void setUp() {
        service = new UserAuthServiceImp(
                userRepository,
                passwordEncoder,
                authenticationManager,
                refreshTokenRepository,
                eventPublisher,
                accountActivationService);
    }

    @Test
    void itAdminCannotCreateSuperAdmin() {
        _User actor = actor(UserRole.IT_ADMIN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.userAccountCreation(actor, request(UserRole.SUPER_ADMIN)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void authenticatedUserWithoutAccountCreationRoleIsForbidden() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.userAccountCreation(actor(UserRole.REGISTRAR), request(UserRole.STUDENT)));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void missingPrincipalRemainsUnauthorizedAtServiceBoundary() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.userAccountCreation(null, request(UserRole.STUDENT)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void itAdminCanCreateOperationalAndItAdminAccounts() {
        _User actor = actor(UserRole.IT_ADMIN);
        when(userRepository.save(any(_User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountActivationService.createPendingPasswordHash()).thenReturn("unusable-hash");
        for (UserRole role : new UserRole[]{
                UserRole.IT_ADMIN, UserRole.REGISTRAR, UserRole.DEAN,
                UserRole.HOD, UserRole.BURSAR, UserRole.ACCOUNTANT}) {
            service.userAccountCreation(actor, request(role));
        }

        ArgumentCaptor<_User> users = ArgumentCaptor.forClass(_User.class);
        verify(userRepository, times(6)).save(users.capture());
        assertEquals(
                java.util.List.of(UserRole.IT_ADMIN, UserRole.REGISTRAR, UserRole.DEAN,
                        UserRole.HOD, UserRole.BURSAR, UserRole.ACCOUNTANT),
                users.getAllValues().stream().map(_User::getUserRole).toList());
        assertTrue(users.getAllValues().stream().allMatch(user -> Boolean.FALSE.equals(user.getIsActive())));
        assertTrue(users.getAllValues().stream().allMatch(user -> "unusable-hash".equals(user.getPassword())));
        verify(accountActivationService, times(6)).issueInitialActivation(any(_User.class));
    }

    @Test
    void superAdminCanCreateSuperAdmin() {
        _User actor = actor(UserRole.SUPER_ADMIN);
        when(userRepository.save(any(_User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountActivationService.createPendingPasswordHash()).thenReturn("unusable-hash");
        service.userAccountCreation(actor, request(UserRole.SUPER_ADMIN));

        verify(userRepository).save(argThat(user -> user.getUserRole() == UserRole.SUPER_ADMIN));
        verify(accountActivationService).issueInitialActivation(any(_User.class));
    }

    @Test
    void genericCreationRejectsStudentForBothAdminRoles() {
        for (UserRole actorRole : new UserRole[]{UserRole.IT_ADMIN, UserRole.SUPER_ADMIN}) {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.userAccountCreation(actor(actorRole), request(UserRole.STUDENT)));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        verifyNoInteractions(userRepository, passwordEncoder, eventPublisher);
    }

    @Test
    void refreshRejectsInactiveAccountWithoutChangingToken() {
        assertRefreshRejectedWithoutChangingToken(false, true, "Account is inactive");
    }

    @Test
    void refreshRejectsBannedAccountWithoutChangingToken() {
        assertRefreshRejectedWithoutChangingToken(true, false, "Account is banned");
    }

    @Test
    void activeUnbannedAccountRefreshRotatesTokenAndRevokesOldToken() {
        _User user = actor(UserRole.STUDENT);
        user.setIsActive(true);
        user.setIsBanned(false);
        RefreshToken storedToken = storedToken(user, false);
        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<JwtUtil> jwt = mockStatic(JwtUtil.class)) {
            jwt.when(() -> JwtUtil.extractEmail("valid-refresh")).thenReturn(user.getEmail());
            jwt.when(() -> JwtUtil.isTokenExpired("valid-refresh")).thenReturn(false);
            jwt.when(() -> JwtUtil.generateAccessToken(user)).thenReturn("new-access");
            jwt.when(() -> JwtUtil.generateRefreshToken(user)).thenReturn("new-refresh");
            jwt.when(() -> JwtUtil.extractExpiration("new-refresh")).thenReturn(new Date());

            AuthResponse response = service.refreshToken(refreshRequest());

            assertEquals("new-access", response.token());
            assertEquals("new-refresh", response.refreshToken());
        }

        assertTrue(storedToken.getIsRevoked());
        ArgumentCaptor<RefreshToken> tokens = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(tokens.capture());
        assertSame(storedToken, tokens.getAllValues().get(0));
        assertEquals("new-refresh", tokens.getAllValues().get(1).getToken());
        assertFalse(tokens.getAllValues().get(1).getIsRevoked());
    }

    @Test
    void refreshRejectsPreviouslyRevokedToken() {
        _User user = actor(UserRole.STUDENT);
        RefreshToken storedToken = storedToken(user, true);
        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.of(storedToken));

        try (MockedStatic<JwtUtil> jwt = mockStatic(JwtUtil.class)) {
            jwt.when(() -> JwtUtil.extractEmail("valid-refresh")).thenReturn(user.getEmail());
            jwt.when(() -> JwtUtil.isTokenExpired("valid-refresh")).thenReturn(false);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.refreshToken(refreshRequest()));
            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        }

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRejectsUnknownSubmittedCredentialAsUnauthorized() {
        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.empty());

        try (MockedStatic<JwtUtil> jwt = mockStatic(JwtUtil.class)) {
            jwt.when(() -> JwtUtil.extractEmail("valid-refresh")).thenReturn("actor@example.test");
            jwt.when(() -> JwtUtil.isTokenExpired("valid-refresh")).thenReturn(false);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.refreshToken(refreshRequest()));

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            assertEquals("Invalid refresh token", exception.getReason());
        }

        verify(refreshTokenRepository, never()).save(any());
    }

    private void assertRefreshRejectedWithoutChangingToken(
            boolean active,
            boolean unbanned,
            String expectedMessage) {
        _User user = actor(UserRole.STUDENT);
        user.setIsActive(active);
        user.setIsBanned(!unbanned);
        RefreshToken storedToken = storedToken(user, false);
        when(refreshTokenRepository.findByToken("valid-refresh")).thenReturn(Optional.of(storedToken));

        try (MockedStatic<JwtUtil> jwt = mockStatic(JwtUtil.class)) {
            jwt.when(() -> JwtUtil.extractEmail("valid-refresh")).thenReturn(user.getEmail());
            jwt.when(() -> JwtUtil.isTokenExpired("valid-refresh")).thenReturn(false);

            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.refreshToken(refreshRequest()));

            assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
            assertTrue(exception.getReason().contains(expectedMessage));
            jwt.verify(() -> JwtUtil.generateAccessToken(user), never());
            jwt.verify(() -> JwtUtil.generateRefreshToken(user), never());
        }

        assertFalse(storedToken.getIsRevoked());
        verify(refreshTokenRepository, never()).save(any());
    }

    private _User actor(UserRole role) {
        return _User.builder()
                .id(java.util.UUID.randomUUID())
                .email("actor@example.test")
                .fullName("Test Actor")
                .userRole(role)
                .password("existing")
                .isActive(true)
                .isBanned(false)
                .build();
    }

    private UsersAccountCreationRequest request(UserRole role) {
        return new UsersAccountCreationRequest(role, "new@example.test", "New User");
    }

    private RefreshTokenRequest refreshRequest() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh");
        return request;
    }

    private RefreshToken storedToken(_User user, boolean revoked) {
        return RefreshToken.builder()
                .user(user)
                .token("valid-refresh")
                .expiresAt(new Date(System.currentTimeMillis() + 60_000))
                .isRevoked(revoked)
                .build();
    }
}
