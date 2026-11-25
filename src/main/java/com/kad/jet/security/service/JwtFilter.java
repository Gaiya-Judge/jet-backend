package com.kad.jet.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.exception.ErrorResponse;
import com.kad.jet.user.model._User;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetails;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        //get authorization header from request
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        //extract token
        final String jwt = authHeader.substring(7);

        final String email;
        try {
            if (!JwtUtil.isTokenType(jwt, JwtUtil.TokenType.ACCESS)) {
                writeInvalidTokenResponse(request, response, null);
                return;
            }
            email = JwtUtil.extractEmail(jwt);
        } catch (JwtException | IllegalArgumentException e) {
            writeInvalidTokenResponse(request, response, e);
            return;
        }

        if (email == null) {
            writeInvalidTokenResponse(request, response, null);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            final _User user;
            try {
                user = (_User) customUserDetails.loadUserByUsername(email);
            } catch (AuthenticationException e) {
                writeInvalidTokenResponse(request, response, e);
                return;
            }

            final boolean tokenValid;
            try {
                tokenValid = JwtUtil.isTokenValid(jwt, user);
            } catch (JwtException | IllegalArgumentException e) {
                writeInvalidTokenResponse(request, response, e);
                return;
            }

            if (!tokenValid || !user.isEnabled() || !user.isAccountNonLocked()) {
                writeInvalidTokenResponse(request, response, null);
                return;
            }

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }

        // Keep downstream application exceptions outside token-validation handling.
        filterChain.doFilter(request, response);
    }

    private void writeInvalidTokenResponse(HttpServletRequest request, HttpServletResponse response,
                                           Exception exception) throws IOException {
        if (exception != null) {
            log.warn("JWT authentication failed: {}", exception.getMessage());
        }
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                HttpServletResponse.SC_UNAUTHORIZED,
                request.getRequestURI(),
                "invalid or expired access token",
                "unauthorized"));
    }
}
