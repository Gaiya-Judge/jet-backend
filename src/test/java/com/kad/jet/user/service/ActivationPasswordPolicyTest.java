package com.kad.jet.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ActivationPasswordPolicyTest {

    private final ActivationPasswordPolicy policy = new ActivationPasswordPolicy();

    ActivationPasswordPolicyTest() {
        ReflectionTestUtils.setField(policy, "minimumLength", 12);
    }

    @Test
    void acceptsLongPasswordContainingLettersAndDigits() {
        assertDoesNotThrow(() -> policy.validate("SecurePassword2026"));
    }

    @Test
    void rejectsShortPasswordAndPasswordWithoutDigit() {
        ResponseStatusException shortPassword = assertThrows(
                ResponseStatusException.class,
                () -> policy.validate("Short123"));
        ResponseStatusException noDigit = assertThrows(
                ResponseStatusException.class,
                () -> policy.validate("PasswordWithoutNumber"));
        assertEquals(HttpStatus.BAD_REQUEST, shortPassword.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, noDigit.getStatusCode());
    }

    @Test
    void rejectsPasswordsThatExceedBcryptInputLimit() {
        String tooLong = "A".repeat(70) + "1" + "é";
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> policy.validate(tooLong));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
