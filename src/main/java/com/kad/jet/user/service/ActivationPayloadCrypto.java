package com.kad.jet.user.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Component
public class ActivationPayloadCrypto {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${activation.outbox.encryption-key:}")
    private String encodedKey;

    private byte[] key;

    @PostConstruct
    void initialize() {
        if (encodedKey == null || encodedKey.isBlank()) {
            log.warn("Activation outbox encryption key is not configured; activation email delivery is disabled");
            return;
        }
        try {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                throw new IllegalArgumentException("key must decode to 32 bytes");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ACTIVATION_OUTBOX_ENCRYPTION_KEY must be base64 for 32 random bytes");
        }
    }

    public byte[] encrypt(String plaintext, String associatedData) {
        if (key == null) {
            throw new IllegalStateException("Activation outbox encryption is not configured");
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Could not protect activation email payload");
        }
    }

    public String decrypt(byte[] encrypted, String associatedData) {
        if (key == null) {
            throw new IllegalStateException("Activation outbox encryption is not configured");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(encrypted, 0, NONCE_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, NONCE_LENGTH, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read protected activation email payload");
        }
    }
}
