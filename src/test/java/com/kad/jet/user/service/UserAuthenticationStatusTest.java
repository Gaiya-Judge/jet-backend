package com.kad.jet.user.service;

import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserAuthenticationStatusTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void inactiveAccountCannotLogin() {
        _User user = user(false, false);

        assertThrows(DisabledException.class, () -> authenticate(user));
    }

    @Test
    void bannedAccountCannotLogin() {
        _User user = user(true, true);

        assertThrows(LockedException.class, () -> authenticate(user));
    }

    @Test
    void activeUnbannedAccountCanLoginAfterActivation() {
        _User user = user(true, false);

        assertTrue(authenticate(user).isAuthenticated());
    }

    private org.springframework.security.core.Authentication authenticate(_User user) {
        UserDetailsService userDetailsService = ignored -> user;
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider).authenticate(
                new UsernamePasswordAuthenticationToken("user@example.test", "ActivationPassword2026"));
    }

    private _User user(boolean active, boolean banned) {
        return _User.builder()
                .email("user@example.test")
                .fullName("Test User")
                .password(encoder.encode("ActivationPassword2026"))
                .userRole(UserRole.REGISTRAR)
                .isActive(active)
                .isBanned(banned)
                .build();
    }
}
