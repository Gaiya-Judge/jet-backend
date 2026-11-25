package com.kad.jet.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class WebhookEventIdentity {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    private WebhookEventIdentity() {
    }

    static String create(
            String provider,
            String eventType,
            String reference,
            Map<String, Object> data,
            String rawPayload) {
        Object providerEventId = data == null ? null : data.get("id");
        String identitySource;
        if (providerEventId != null
                && !providerEventId.toString().isBlank()) {
            identitySource = provider + "|" + eventType + "|"
                    + reference + "|" + providerEventId;
        } else {
            identitySource = provider + "|payload|"
                    + canonicalize(rawPayload);
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identitySource.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable for webhook event identity",
                    exception);
        }
    }

    private static String canonicalize(String rawPayload) {
        try {
            JsonNode payload = OBJECT_MAPPER.readTree(rawPayload);
            return OBJECT_MAPPER.writeValueAsString(sortObjectKeys(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Cannot canonicalize webhook payload", exception);
        }
    }

    private static JsonNode sortObjectKeys(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sortedObject = OBJECT_MAPPER.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> fields = node.fieldNames();
            fields.forEachRemaining(fieldNames::add);
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                sortedObject.set(
                        fieldName, sortObjectKeys(node.get(fieldName)));
            }
            return sortedObject;
        }

        if (node.isArray()) {
            ArrayNode sortedArray = OBJECT_MAPPER.createArrayNode();
            for (JsonNode element : node) {
                sortedArray.add(sortObjectKeys(element));
            }
            return sortedArray;
        }

        return node.deepCopy();
    }
}
