package com.kad.jet.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Component
public class ActivationPasswordPolicy {

    @Value("${activation.password.minimum-length:12}")
    private int minimumLength;

    public void validate(String password) {
        if (password == null
                || password.length() < minimumLength
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least " + minimumLength +
                            " characters and include a letter and a number");
        }
    }
}
