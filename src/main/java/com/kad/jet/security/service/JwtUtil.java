package com.kad.jet.security.service;

import com.kad.jet.user.model._User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtUtil {

    private static String jwtSecretKey;
    private static long jwtAccessKeyExpiration;
    private static long jwtRefreshTokenKeyExpiration;

    @Value("${jwt.secret}")
    public void setSecretKey(String secretKey){
        JwtUtil.jwtSecretKey = secretKey;
    }

    @Value("${jwt.expiration-ms}")
    public void setJwtAccessKeyExpiration(long accessKeyExpiration){
        JwtUtil.jwtAccessKeyExpiration =accessKeyExpiration;
    }

    @Value("${jwt.refresh-expiration-ms}")
    public void setJwtRefreshTokenKeyExpiration(long refreshTokenKeyExpiration){
        JwtUtil.jwtRefreshTokenKeyExpiration=refreshTokenKeyExpiration;
    }

    public static SecretKey getSigningKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT secret key must be configured");
        }

        byte[] keyBytes;
        try {
            // 1. Logic: If it looks like Hex, parse it as Hex.
            // Otherwise, assume it's a plain string.
            if (isHexString(secretKey)) {
                keyBytes = HexFormat.of().parseHex(secretKey);
            } else {
                keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            }

            // 2. Validate security requirement: HS256 requires at least 32 bytes
            if (keyBytes.length < 32) {
                throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
            }

            return Keys.hmacShaKeyFor(keyBytes);

        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid format for JWT secret key: " + e.getMessage());
        }
    }

    //generate token
    public static String jwtBuilder(_User user, long expiration){
        Map<String, Object> claims = new HashMap<>();

        //insert claims
        claims.put("userId", user.getId().toString());
        claims.put("userAuthority", user.getAuthorities());

        return Jwts
                .builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(jwtSecretKey))
                .compact();
    }

    //extract all claims from token
    public static Claims extractAllClaims(String token){
        return Jwts
                .parser()
                .verifyWith(getSigningKey(jwtSecretKey))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //method to extract single claim
    public static <T> T extractClaim(String token, Function<Claims, T> claimsResolver){
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    //username extracted from token
    public static String extractEmail(String token){
        return extractClaim(token, Claims::getSubject);
    }

    //extracting token expiration
    public static Date extractExpiration(String token){
        return extractClaim(token, Claims::getExpiration);
    }

    //method to check if token is not expired
    public static boolean isTokenExpired(String token){
        return extractExpiration(token).before(new Date());
    }

    //if method is valid
    public static boolean isTokenValid(String token, _User user){
        String email = extractEmail(token);
        return email.equals(user.getUsername()) && !isTokenExpired(token);
    }

    //generate accessToken
    public static String generateAccessToken(_User user){
        return jwtBuilder(user, jwtAccessKeyExpiration);
    }

    public static String generateRefreshToken(_User user){
        return jwtBuilder(user, jwtRefreshTokenKeyExpiration);
    }


    public static UUID extractUserId(String token){
        return extractClaim(token, claims -> UUID.fromString(claims.get("userId", String.class)));
    }


    //hex-decimal string check
    private static boolean isHexString(String str) {
        // Basic check: Hex strings are usually even-length and contain only 0-9, a-f, A-F
        return str.matches("^[0-9a-fA-F]+$") && (str.length() % 2 == 0);
    }
}
