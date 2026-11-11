package com.kad.jet.registration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kad.jet.registration.service.RegistrationService;
import com.kad.jet.security.SecurityConfig;
import com.kad.jet.security.service.CustomUserDetailsService;
import com.kad.jet.security.service.JwtFilter;
import com.kad.jet.security.service.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationController.class)
@Import({SecurityConfig.class, RegistrationBulkRequestValidationTest.TestBeans.class})
class RegistrationBulkRequestValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private RegistrationService registrationService;
    @MockitoBean private CustomUserDetailsService customUserDetails;

    @TestConfiguration
    static class TestBeans {
        @Bean
        JwtFilter jwtFilter(CustomUserDetailsService userDetails, ObjectMapper objectMapper) {
            return new JwtFilter(new JwtUtil(), userDetails, objectMapper);
        }
    }

    @Test
    @WithMockUser
    void missingBulkRequestBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/registration/{id}/bulk", "4a5804bb-37ea-4418-9b30-cc1bd657b8d5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    @WithMockUser
    void nullCourseIdListReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/registration/{id}/bulk", "4a5804bb-37ea-4418-9b30-cc1bd657b8d5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\":null}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    @WithMockUser
    void emptyCourseIdListReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/registration/{id}/bulk", "4a5804bb-37ea-4418-9b30-cc1bd657b8d5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }
}
