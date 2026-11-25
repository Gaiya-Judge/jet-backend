package com.kad.jet.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.security.service.CustomUserDetailsService;
import com.kad.jet.security.service.JwtFilter;
import com.kad.jet.security.service.JwtUtil;
import com.kad.jet.user.model.UserRole;
import com.kad.jet.user.model._User;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityExceptionHandlingTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long";
    private static final String OTHER_SECRET = "different-test-secret-key-at-least-32-bytes";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private CustomUserDetailsService userDetails;
    private JwtFilter jwtFilter;
    private _User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        JwtUtil util = new JwtUtil();
        util.setSecretKey(SECRET);
        util.setJwtAccessKeyExpiration(60_000);
        util.setJwtRefreshTokenKeyExpiration(120_000);
        userDetails = mock(CustomUserDetailsService.class);
        jwtFilter = new JwtFilter(util, userDetails, objectMapper);
        user = _User.builder()
                .id(UUID.randomUUID())
                .email("user@example.test")
                .fullName("Test User")
                .password("unused")
                .userRole(UserRole.REGISTRAR)
                .isActive(true)
                .isBanned(false)
                .build();
        when(userDetails.loadUserByUsername(user.getEmail())).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void directAuthenticationEntryPointInvocationReturns401WithJetErrorResponse() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityConfig(jwtFilter, userDetails, objectMapper)
                .authenticationEntryPoint().commence(request, response,
                        new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("missing"));

        assertErrorResponse(response, 401, "unauthorized", "authentication required");
    }

    @Test
    void malformedJwtReturns401AndClearsSecurityContext() throws Exception {
        assertInvalidJwtReturns401("Bearer definitely-not-a-jwt");
    }

    @Test
    void expiredJwtReturns401AndClearsSecurityContext() throws Exception {
        String token = JwtUtil.jwtBuilder(user, -10_000);
        assertInvalidJwtReturns401("Bearer " + token);
    }

    @Test
    void invalidSignatureJwtReturns401AndClearsSecurityContext() throws Exception {
        String token = Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(JwtUtil.getSigningKey(OTHER_SECRET))
                .compact();
        assertInvalidJwtReturns401("Bearer " + token);
    }

    @Test
    void validJwtAuthenticatesUserAndContinuesFilterChain() throws Exception {
        String token = JwtUtil.generateAccessToken(user);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        jwtFilter.doFilter(request, response, chain);

        assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
    }

    @Test
    void validRefreshTokenIsRejectedAsAnAccessToken() throws Exception {
        assertInvalidJwtReturns401(
                "Bearer " + JwtUtil.generateRefreshToken(user));

        verifyNoInteractions(userDetails);
    }

    @Test
    void jwtWithoutTokenTypeIsRejected() throws Exception {
        assertInvalidJwtReturns401(
                "Bearer " + signedToken(false, null));

        verifyNoInteractions(userDetails);
    }

    @Test
    void jwtWithUnsupportedTokenTypeIsRejected() throws Exception {
        assertInvalidJwtReturns401(
                "Bearer " + signedToken(true, "BEARER"));

        verifyNoInteractions(userDetails);
    }

    @Test
    void downstreamExceptionIsNotConvertedToInvalidTokenResponse() throws Exception {
        String token = JwtUtil.generateAccessToken(user);
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> { throw new jakarta.servlet.ServletException("downstream failure"); };

        assertThrows(jakarta.servlet.ServletException.class,
                () -> jwtFilter.doFilter(request, response, chain));
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().isEmpty());
    }

    @Test
    void accessDeniedHandlerReturns403WithJetErrorResponse() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SecurityConfig(jwtFilter, userDetails, objectMapper)
                .accessDeniedHandler().handle(request, response, new AccessDeniedException("denied"));

        assertErrorResponse(response, 403, "access denied", "you do not have permission to perform this action");
    }

    private void assertInvalidJwtReturns401(String authorization) throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader("Authorization", authorization);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("stale", "credentials"));

        jwtFilter.doFilter(request, response, chain);

        assertErrorResponse(response, 401, "unauthorized", "invalid or expired access token");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(chain);
    }

    private void assertErrorResponse(MockHttpServletResponse response, int status,
                                     String error, String message) throws Exception {
        assertEquals(status, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(status, body.get("status").asInt());
        assertEquals(error, body.get("error").asText());
        assertEquals(message, body.get("message").asText());
        assertEquals("/api/protected", body.get("path").asText());
        assertTrue(body.hasNonNull("timestamp"));
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

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/protected");
        request.setServletPath("/api/protected");
        return request;
    }
}
