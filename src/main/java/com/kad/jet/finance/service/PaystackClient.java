package com.kad.jet.finance.service;

import com.kad.jet.config.PaystackConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaystackClient {

    private final PaystackConfig paystackConfig;
    private final RestTemplate restTemplate;

    public Map<String, Object> initializeTransaction(String email, BigDecimal amountInNaira, String reference){
        //paystack required amount in kobo
        //1 naira = 100 kobo
        long amountInKobo = amountInNaira
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("amount", amountInKobo);
        body.put("reference", reference);
        body.put("currency", "NGN");

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, buildHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    paystackConfig.getBaseUrl() +
                            "/transaction/verify/" + reference,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null ||
                    !(Boolean) responseBody.get("status")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "paystack verification failed");
            }

            return (Map<String, Object>) responseBody.get("data");

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paystack verify error: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "could not verify payment with paystack");
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(paystackConfig.getSecretKey());
        return headers;
    }
}
