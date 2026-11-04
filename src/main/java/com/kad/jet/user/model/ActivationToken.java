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
@Table(name = "activation_tokens", indexes = {
        @Index(name = "idx_activation_tokens_user", columnList = "user_id"),
        @Index(name = "idx_activation_tokens_expiry", columnList = "expires_at")
})
public class ActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private _User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuance_type", nullable = false, length = 16)
    private ActivationIssuanceType issuanceType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
