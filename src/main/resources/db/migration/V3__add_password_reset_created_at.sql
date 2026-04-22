-- ============================================================
-- V3 — Add created_at to password_reset_tokens
--      Purge existing rows: old plain-text tokens are incompatible
--      with the new hash-based storage introduced in this version.
--      Any in-flight reset links will become invalid; users will
--      need to request a fresh link.
-- ============================================================
TRUNCATE TABLE password_reset_tokens;

ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
