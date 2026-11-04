# Account activation deployment

## Database migration

The project uses Hibernate schema validation in production and has no Flyway or Liquibase setup. Apply `src/main/resources/db/manual/001_account_activation.sql` once to the target PostgreSQL database before deploying the application version that maps the activation and email-outbox tables.

Run it with a PostgreSQL client configured with the normal deployment connection environment, for example:

```powershell
psql -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/001_account_activation.sql
```

The migration only creates the two new tables, constraints, and indexes. It does not alter existing users or account status. Back up the database and confirm the target before applying any production migration.

Account creation stores a BCrypt hash of a fresh, cryptographically random, undisclosed placeholder because the existing `users.password_hash` column is non-null. Administrators never receive this value; the user replaces it with their own password only after presenting a valid activation token. The activation token itself is hashed in `activation_tokens`; the outbox holds it only as AES-GCM ciphertext authenticated against the token row ID and recipient.

## Required runtime environment

Configure these values in the deployment environment; do not commit credentials:

- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`
- `SMTP_AUTH`, `SMTP_SSL_ENABLE`, `SMTP_STARTTLS_ENABLE`, `SMTP_STARTTLS_REQUIRED`
- `SMTP_CONNECTION_TIMEOUT_MS`, `SMTP_TIMEOUT_MS`, `SMTP_WRITE_TIMEOUT_MS`
- `SMTP_FROM_EMAIL`, `SMTP_SENDER_NAME`
- `FRONTEND_ACTIVATION_BASE_URL` — HTTPS frontend origin/base path that serves `/activate`
- `ACTIVATION_OUTBOX_ENCRYPTION_KEY` — base64 encoding of exactly 32 cryptographically random bytes; preserve it across restarts and deployments because pending outbox payloads depend on it

The repository ignores `application-local.yml` because it contains machine-specific configuration. Use `src/main/resources/application-local.example.yml` as the safe placeholder template for local development; the production placeholders are in `application-prod.yml`.

Optional activation settings and defaults are declared in the application profiles: token lifetime (`PT24H`), resend cooldown (`PT60S`), resend window (`PT24H`), maximum resends (`5`), password minimum length (`12`), and outbox polling, batch, and retry settings.

The outbox retries transient failures with exponential backoff (30 seconds initially, capped at 6 hours) for up to 8 attempts by default. Permanent SMTP 5xx failures are marked `FAILED` immediately; other persistent failures become `FAILED` at the attempt limit. Operators can review delivery state and sanitized error codes with:

```sql
SELECT id, recipient, status, attempt_count, last_error_code, created_at, last_attempt_at
FROM email_outbox
WHERE status = 'FAILED'
ORDER BY created_at;
```

## Frontend contract

The email link points to `{FRONTEND_ACTIVATION_BASE_URL}/activate?token=...`. The frontend reads the token, collects a password and confirmation, and submits:

```json
{
  "token": "activation-token-from-link",
  "password": "user-chosen-password",
  "passwordConfirmation": "user-chosen-password"
}
```

to `POST /api/user/auth/activate`. The token should not be sent to analytics or unrelated endpoints. The API returns a success message only; the user then signs in through the normal login endpoint.

Successful activation response:

```text
Account activated successfully
```

Invalid, expired, consumed, or revoked tokens return HTTP 400 with a generic activation-token error. Password confirmation or password-policy failures also return HTTP 400 and do not consume the token.

Resend requests use `POST /api/user/auth/activation/resend` with `{ "email": "user@example.edu" }`. Its response is deliberately generic.

```text
If the account is eligible, an activation email will be sent
```
