package com.kad.jet.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void carryoverCreditLimitReturnsStructuredConflict() {
        var course = new CarryoverCreditLimitException.CourseDetail(
                UUID.randomUUID(), "JET101", "Course", 4);
        var exception = new CarryoverCreditLimitException(3, 4, List.of(course));
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/registration");

        var response = handler.carryoverCreditLimitHandler(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("/api/registration", response.getBody().getPath());
        assertEquals(exception.getMessage(), response.getBody().getMessage());
        var details = (java.util.Map<?, ?>) response.getBody().getDetails();
        assertEquals(3, details.get("maximumCreditUnits"));
        assertEquals(4, details.get("totalCarryoverCreditUnits"));
        assertEquals(List.of(course), details.get("courses"));
    }

    @Test
    void existingErrorResponseConstructorLeavesDetailsAbsent() throws Exception {
        ErrorResponse response = new ErrorResponse(400, "/api/test", "bad input", "Bad Request");

        assertEquals(400, response.getStatus());
        assertEquals("bad input", response.getMessage());
        assertNull(response.getDetails());
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var json = objectMapper.readTree(objectMapper.writeValueAsString(response));
        assertFalse(json.has("details"));
    }
}
