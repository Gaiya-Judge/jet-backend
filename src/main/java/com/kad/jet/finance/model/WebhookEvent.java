package com.kad.jet.finance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Which payment provider sent this webhook
    // Always PAYSTACK for now — ready for future providers
    @Column(nullable = false)
    private String provider;

    // Paystack event type e.g. "charge.success"
    @Column(nullable = false)
    private String eventType;

    // Payment reference from inside the webhook payload
    // Used to find the matching Payment record
    @Column(nullable = false)
    private String reference;

    // Exact JSON string as received from Paystack
    // Never alter this — it is the source of truth
    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    // The x-paystack-signature header value
    // Stored for audit purposes
    @Column(nullable = false)
    private String signature;

    // Did we successfully process this event?
    // false = persisted but not yet processed
    // true  = fully processed
    @Column(nullable = false)
    private Boolean processed = false;

    // If processing failed — why did it fail?
    // Null when processing succeeds
    @Column(columnDefinition = "TEXT")
    private String processingError;

    // When the webhook arrived at our server
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime receivedAt;

    // When we finished processing it
    private LocalDateTime processedAt;
}
