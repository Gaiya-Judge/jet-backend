package com.kad.jet.security;

import com.kad.jet.security.service.JwtUtil;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTokenTypeTest {

    private static final String SECRET =
            "jwt-token-type-test-secret-key-at-least-32-bytes";

    private _User user;

    @BeforeEach
    void setUp() {
        JwtUtil util = new JwtUtil();
        util.setSecretKey(SECRET);
        util.setJwtAccessKeyExpiration(60_000);
        util.setJwtRefreshTokenKeyExpiration(120_000);
        user = _User.builder()
                .id(UUID.randomUUID())
                .email("token-type@example.test")
                .fullName("Token Type Test")
                .userRole(UserRole.STUDENT)
                .isActive(true)
                .isBanned(false)
                .build();
    }

    @Test
    void accessTokenHasAccessTypeAndPreservesExistingClaims() {
        String token = JwtUtil.generateAccessToken(user);

        assertEquals(JwtUtil.TokenType.ACCESS, JwtUtil.extractTokenType(token));
        assertTrue(JwtUtil.isTokenType(token, JwtUtil.TokenType.ACCESS));
        assertEquals(UUID.fromString(JwtUtil.extractAllClaims(token).getId()),
                JwtUtil.extractJti(token));
        assertClaimsPreserved(JwtUtil.extractAllClaims(token));
    }

    @Test
    void refreshTokenHasRefreshTypeAndPreservesExistingClaims() {
        String token = JwtUtil.generateRefreshToken(user);

        assertEquals(JwtUtil.TokenType.REFRESH, JwtUtil.extractTokenType(token));
        assertTrue(JwtUtil.isTokenType(token, JwtUtil.TokenType.REFRESH));
        assertEquals(UUID.fromString(JwtUtil.extractAllClaims(token).getId()),
                JwtUtil.extractJti(token));
        assertClaimsPreserved(JwtUtil.extractAllClaims(token));
    }

    @Test
    void eachGeneratedTokenReceivesAnIndependentJti() {
        UUID accessId = JwtUtil.extractJti(JwtUtil.generateAccessToken(user));
        UUID refreshId = JwtUtil.extractJti(JwtUtil.generateRefreshToken(user));
        UUID secondAccessId = JwtUtil.extractJti(
                JwtUtil.generateAccessToken(user));
        UUID secondRefreshId = JwtUtil.extractJti(
                JwtUtil.generateRefreshToken(user));

        assertNotEquals(accessId, refreshId);
        assertNotEquals(accessId, secondAccessId);
        assertNotEquals(refreshId, secondRefreshId);
    }

    @Test
    void legacyBuilderCreatesAnAccessToken() {
        String token = JwtUtil.jwtBuilder(user, 60_000);

        assertEquals(JwtUtil.TokenType.ACCESS, JwtUtil.extractTokenType(token));
        assertNotNull(JwtUtil.extractJti(token));
    }

    @Test
    void missingNullMalformedAndUnsupportedTokenTypesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractTokenType(signedToken(false, null)));
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractTokenType(signedToken(true, null)));
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractTokenType(signedToken(true, 12)));
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractTokenType(signedToken(true, "BEARER")));
    }

    @Test
    void jtiExtractionRejectsMissingNullAndMalformedValues() {
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractJti(signedToken(false, null)));
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractJti(signedTokenWithJti(null)));
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractJti(signedTokenWithJti("not-a-uuid")));
        assertThrows(IllegalArgumentException.class,
                () -> JwtUtil.extractJti(signedTokenWithJti(12)));
    }

    private void assertClaimsPreserved(Claims claims) {
        assertEquals(user.getUsername(), claims.getSubject());
        assertEquals(user.getId().toString(), claims.get("userId", String.class));
        assertNotNull(claims.get("userAuthority"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    private String signedToken(boolean includeType, Object tokenType) {
        Date now = new Date();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("userAuthority", user.getAuthorities());
        if (includeType) {
            claims.put("tokenType", tokenType);
        }
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(JwtUtil.getSigningKey(SECRET))
                .compact();
    }

    private String signedTokenWithJti(Object jti) {
        Date now = new Date();
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("userAuthority", user.getAuthorities());
        claims.put("tokenType", JwtUtil.TokenType.ACCESS.name());
        claims.put("jti", jti);
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(JwtUtil.getSigningKey(SECRET))
                .compact();
    }
}
