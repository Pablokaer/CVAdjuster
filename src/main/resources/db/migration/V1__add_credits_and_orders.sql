-- ============================================================
-- V1 — Adiciona coluna credits em users e cria tabela orders
-- Seguro para bancos existentes com dados (usa IF NOT EXISTS)
-- ============================================================

-- ── users.credits ────────────────────────────────────────────
-- Passo 1: adiciona como nullable para não falhar com linhas existentes
ALTER TABLE users ADD COLUMN IF NOT EXISTS credits INTEGER;

-- Passo 2: preenche registros existentes que ficaram NULL
UPDATE users SET credits = 0 WHERE credits IS NULL;

-- Passo 3: aplica NOT NULL e default permanente
ALTER TABLE users ALTER COLUMN credits SET NOT NULL;
ALTER TABLE users ALTER COLUMN credits SET DEFAULT 0;

-- ── orders ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id              BIGSERIAL    PRIMARY KEY,
    product_name    VARCHAR(255) NOT NULL,
    amount          BIGINT       NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    stripe_session_id VARCHAR(255) UNIQUE,
    user_id         BIGINT,
    credits_to_add  INTEGER,
    created_at      TIMESTAMP    NOT NULL
);
