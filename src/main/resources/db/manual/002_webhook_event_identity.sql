BEGIN;

ALTER TABLE webhook_events
    ADD COLUMN event_identity VARCHAR(64);

CREATE UNIQUE INDEX uk_webhook_events_event_identity
    ON webhook_events (event_identity)
    WHERE event_identity IS NOT NULL;

COMMIT;
