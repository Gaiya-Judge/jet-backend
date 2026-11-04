package com.kad.jet.user.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "email_outbox", indexes = {
        @Index(name = "idx_email_outbox_ready", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "idx_email_outbox_lease", columnList = "status,lease_until")
})
public class EmailOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private EmailOutboxMessageType messageType;

    // AES-GCM encrypted payload. It contains the raw activation token and is never logged.
    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "BYTEA")
    private byte[] encryptedPayload;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activation_token_id", nullable = false, unique = true)
    private ActivationToken activationToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;
}
