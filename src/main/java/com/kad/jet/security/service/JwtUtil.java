package com.kad.jet.security.service;

import com.kad.jet.user.model._User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    //key encoder
    public static SecretKey getSigningKey(String secretKey){
        if (JwtUtil.jwtSecretKey == null){
            throw new RuntimeException("secret key not yet configured");
        }

        try {
            byte[] keyByte = Decoders.BASE64.decode(secretKey);
            return Keys.hmacShaKeyFor(keyByte);
        }catch (RuntimeException e){
            throw new RuntimeException("signing key malfunction " + e.getMessage());
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
}
