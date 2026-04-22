-- ============================================================
-- V4 — Stripe webhook idempotency log
--
-- Every successfully processed Stripe event is recorded here
-- before the transaction commits. The UNIQUE constraint on
-- stripe_event_id is the actual race serializer: if two
-- concurrent requests try to process the same event, only
-- one INSERT succeeds; the other rolls back the entire
-- transaction (including any credit grant), and Stripe's
-- retry will then see the event as already processed.
-- ============================================================
CREATE TABLE IF NOT EXISTS stripe_processed_events (
    id               BIGSERIAL    PRIMARY KEY,
    stripe_event_id  VARCHAR(255) NOT NULL,
    event_type       VARCHAR(100) NOT NULL,
    processed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stripe_event_id UNIQUE (stripe_event_id)
);
