BEGIN;

CREATE TABLE activation_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    issuance_type VARCHAR(16) NOT NULL CHECK (issuance_type IN ('INITIAL', 'RESEND')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_activation_tokens_user ON activation_tokens(user_id);
CREATE INDEX idx_activation_tokens_expiry ON activation_tokens(expires_at);

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(320) NOT NULL,
    message_type VARCHAR(40) NOT NULL CHECK (message_type = 'ACCOUNT_ACTIVATION'),
    encrypted_payload BYTEA NOT NULL,
    activation_token_id UUID NOT NULL UNIQUE REFERENCES activation_tokens(id),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'CANCELLED', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(80) NULL
);

CREATE INDEX idx_email_outbox_ready ON email_outbox(status, next_attempt_at, created_at);
CREATE INDEX idx_email_outbox_lease ON email_outbox(status, lease_until);

COMMIT;
