package com.kad.jet.finance.service;

import com.kad.jet.config.PaystackConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaystackAmountPrecisionTest {

    @Mock private PaystackConfig paystackConfig;
    @Mock private RestTemplate restTemplate;

    private PaystackClient paystackClient;

    @BeforeEach
    void setUp() {
        paystackClient = new PaystackClient(paystackConfig, restTemplate);
    }

    @Test
    void initializesNairaAmountAsExactKobo() {
        stubSuccessfulInitialization();

        paystackClient.initializeTransaction(
                "student@example.test",
                new BigDecimal("123.45"),
                "reference");

        assertInitializedKoboAmount(12345L);
    }

    @Test
    void acceptsNairaAmountWithExtraTrailingZeroes() {
        stubSuccessfulInitialization();

        paystackClient.initializeTransaction(
                "student@example.test",
                new BigDecimal("10.000"),
                "reference");

        assertInitializedKoboAmount(1000L);
    }

    @Test
    void rejectsFractionalKoboBeforeCallingPaystack() {
        assertThrows(IllegalArgumentException.class,
                () -> paystackClient.initializeTransaction(
                        "student@example.test",
                        new BigDecimal("10.009"),
                        "reference"));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void rejectsOutboundKoboAmountOutsideLongRange() {
        assertThrows(IllegalArgumentException.class,
                () -> paystackClient.initializeTransaction(
                        "student@example.test",
                        new BigDecimal("92233720368547758.08"),
                        "reference"));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void parsesIntegerKoboAmountExactly() {
        assertEquals(new BigDecimal("123.45"),
                PaystackAmountConverter.toNaira(12345));
    }

    @Test
    void rejectsFractionalProviderKobo() {
        assertThrows(IllegalArgumentException.class,
                () -> PaystackAmountConverter.toNaira(
                        new BigDecimal("12345.9")));
    }

    @Test
    void rejectsProviderKoboOutsideLongRange() {
        BigInteger outOfRangeKobo =
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        assertThrows(IllegalArgumentException.class,
                () -> PaystackAmountConverter.toNaira(outOfRangeKobo));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void stubSuccessfulInitialization() {
        when(paystackConfig.getBaseUrl()).thenReturn("https://paystack.test");
        when(paystackConfig.getSecretKey()).thenReturn("test-secret");
        Map responseBody = Map.of(
                "status", true,
                "data", Map.of(
                        "authorization_url", "https://paystack.test/authorize",
                        "access_code", "access-code"));
        ResponseEntity<Map> response = ResponseEntity.ok(responseBody);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)))
                .thenReturn(response);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertInitializedKoboAmount(long expectedAmount) {
        ArgumentCaptor<HttpEntity> requestCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                requestCaptor.capture(),
                eq(Map.class));
        Map<?, ?> requestBody =
                (Map<?, ?>) requestCaptor.getValue().getBody();
        assertEquals(expectedAmount, requestBody.get("amount"));
    }
}
