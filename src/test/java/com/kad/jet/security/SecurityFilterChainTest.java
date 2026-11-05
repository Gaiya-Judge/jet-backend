package com.kad.jet.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.config.controller.SystemConfigController;
import com.kad.jet.config.service.SystemConfigApiService;
import com.kad.jet.security.service.CustomUserDetailsService;
import com.kad.jet.security.service.JwtFilter;
import com.kad.jet.security.service.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebMvcTest(SystemConfigController.class)
@Import({SecurityConfig.class, SecurityFilterChainTest.TestBeans.class})
class SecurityFilterChainTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private CustomUserDetailsService customUserDetails;
    @MockitoBean private SystemConfigApiService systemConfigApiService;

    @TestConfiguration
    static class TestBeans {
        @Bean
        JwtFilter jwtFilter(CustomUserDetailsService userDetails, ObjectMapper objectMapper) {
            return new JwtFilter(new JwtUtil(), userDetails, objectMapper);
        }
    }

    @Test
    void protectedRequestWithoutTokenReturns401JetErrorResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/config"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("application/json"));
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(401, response.get("status").asInt());
        assertEquals("unauthorized", response.get("error").asText());
        assertEquals("authentication required", response.get("message").asText());
        assertEquals("/api/config", response.get("path").asText());
        assertTrue(response.hasNonNull("timestamp"));
    }
}
