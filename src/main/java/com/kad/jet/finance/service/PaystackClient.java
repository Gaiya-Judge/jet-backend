package com.kad.jet.finance.service;

import com.kad.jet.config.PaystackConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PaystackClient {

    private final PaystackConfig paystackConfig;
    private final RestTemplate restTemplate;

    public PaystackClient(
            PaystackConfig paystackConfig,
            @Qualifier("paystackRestTemplate") RestTemplate restTemplate) {
        this.paystackConfig = paystackConfig;
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> initializeTransaction(
            String email,
            BigDecimal amountInNaira,
            String reference) {

        // Paystack requires amount in KOBO not Naira
        // 1 Naira = 100 kobo
        // ₦5,000 = 500,000 kobo
        long amountInKobo = PaystackAmountConverter.toKobo(amountInNaira);

        //put all required body in the send request
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("amount", amountInKobo);
        body.put("reference", reference);
        body.put("currency", "NGN");

        log.info("Initializing Paystack transaction. " +
                        "Reference: {} Amount: ₦{}",
                reference, amountInNaira);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    paystackConfig.getBaseUrl() +
                            "/transaction/initialize",
                    HttpMethod.POST,
                    new HttpEntity<>(body, buildHeaders()),
                    Map.class);

            //getting response from paystack client
            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null ||
                    !Boolean.TRUE.equals(responseBody.get("status"))) {
                Object responseMessage = responseBody == null
                        ? null : responseBody.get("message");
                String message = responseMessage instanceof String text
                        ? text : "Paystack did not confirm initialization";
                log.warn("Paystack initialization response did not confirm success: {}",
                        message);
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "could not confirm payment initialization: " + message);
            }

            Object data = responseBody.get("data");
            if (!(data instanceof Map<?, ?> initializationData)
                    || !(initializationData.get("authorization_url")
                    instanceof String authorizationUrl)
                    || authorizationUrl.isBlank()
                    || !(initializationData.get("access_code")
                    instanceof String accessCode)
                    || accessCode.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Paystack returned an unexpected initialization response");
            }

            log.info("Paystack transaction initialized " +
                    "successfully. Reference: {}", reference);

            return (Map<String, Object>) initializationData;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Paystack initialization did not produce a confirmed response: {}",
                    e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "could not confirm the payment provider response. " +
                            "please check the payment status later.",
                    e);
        }
    }

    public Map<String, Object> verifyTransaction(String reference) {

        log.info("Verifying transaction with Paystack. " +
                "Reference: {}", reference);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    paystackConfig.getBaseUrl() +
                            "/transaction/verify/" + reference,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
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
            log.error("Paystack verification error: {}",
                    e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "could not verify payment with Paystack");
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Bearer authentication — Paystack reads the secret key
        // and knows which account this request belongs to
        headers.setBearerAuth(paystackConfig.getSecretKey());
        return headers;
    }
}
