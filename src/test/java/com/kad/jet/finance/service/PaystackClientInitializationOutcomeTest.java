package com.kad.jet.finance.service;

import com.kad.jet.config.PaystackConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class PaystackClientInitializationOutcomeTest {

    @Mock private PaystackConfig paystackConfig;
    @Mock private RestTemplate restTemplate;

    private PaystackClient paystackClient;

    @BeforeEach
    void setUp() {
        when(paystackConfig.getBaseUrl()).thenReturn("https://paystack.test");
        when(paystackConfig.getSecretKey()).thenReturn("test-secret");
        paystackClient = new PaystackClient(paystackConfig, restTemplate);
    }

    @Test
    void returnsDataFromWellFormedSuccessfulInitialization() {
        stubResponse(Map.of(
                "status", true,
                "data", Map.of(
                        "authorization_url", "https://paystack.test/authorize",
                        "access_code", "access-code")));

        Map<String, Object> response = paystackClient.initializeTransaction(
                "student@example.test",
                BigDecimal.TEN,
                "reference");

        assertEquals("https://paystack.test/authorize",
                response.get("authorization_url"));
        assertEquals("access-code", response.get("access_code"));
    }

    @Test
    void falseProviderEnvelopeIsNotTreatedAsProofThatNoTransactionExists() {
        stubResponse(Map.of(
                "status", false,
                "message", "The request could not be completed"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paystackClient.initializeTransaction(
                        "student@example.test",
                        BigDecimal.TEN,
                        "reference"));

        assertEquals(BAD_GATEWAY, exception.getStatusCode());
    }

    @Test
    void malformedSuccessfulEnvelopeIsRejectedAsUnconfirmed() {
        stubResponse(Map.of(
                "status", true,
                "data", Map.of("authorization_url", "https://paystack.test/authorize")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paystackClient.initializeTransaction(
                        "student@example.test",
                        BigDecimal.TEN,
                        "reference"));

        assertEquals(BAD_GATEWAY, exception.getStatusCode());
        assertTrue(exception.getReason().contains("unexpected initialization response"));
    }

    @Test
    void wrapsHttpServerErrorWithoutClaimingTransactionFailure() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenThrow(new HttpServerErrorException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Paystack server error"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paystackClient.initializeTransaction(
                        "student@example.test",
                        BigDecimal.TEN,
                        "reference"));

        assertEquals(BAD_GATEWAY, exception.getStatusCode());
        assertTrue(exception.getCause() instanceof HttpServerErrorException);
    }

    @Test
    void wrapsTimeoutWithoutClaimingTransactionFailure() {
        ResourceAccessException timeout = new ResourceAccessException(
                "read timed out",
                new SocketTimeoutException("read timed out"));
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenThrow(timeout);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> paystackClient.initializeTransaction(
                        "student@example.test",
                        BigDecimal.TEN,
                        "reference"));

        assertEquals(BAD_GATEWAY, exception.getStatusCode());
        assertTrue(exception.getCause() instanceof ResourceAccessException);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubResponse(Map responseBody) {
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(response);
    }
}
