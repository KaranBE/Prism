CREATE TABLE virtual_keys (
    id                  BIGSERIAL PRIMARY KEY,
    key_value           VARCHAR(128) NOT NULL,
    alias               VARCHAR(64)  NOT NULL,
    requests_per_minute INTEGER      NOT NULL,
    monthly_budget_usd  NUMERIC(12,4) NOT NULL,
    monthly_spend_usd   NUMERIC(12,4) NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX idx_virtual_keys_key_value ON virtual_keys (key_value);

CREATE TABLE virtual_key_allowed_models (
    virtual_key_id BIGINT NOT NULL REFERENCES virtual_keys (id) ON DELETE CASCADE,
    model_name     VARCHAR(64) NOT NULL
);
CREATE INDEX idx_vkam_key_id ON virtual_key_allowed_models (virtual_key_id);

CREATE TABLE request_logs (
    id                BIGSERIAL PRIMARY KEY,
    virtual_key_id    BIGINT NOT NULL,
    requested_model   VARCHAR(64) NOT NULL,
    resolved_model    VARCHAR(64),
    provider          VARCHAR(64),
    status            VARCHAR(32) NOT NULL,
    prompt_tokens     INTEGER,
    completion_tokens INTEGER,
    cost_usd          NUMERIC(12,6),
    cache_hit         BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_used     BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms        BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Composite index backing "usage for key X over time range Y" - the hottest read query
-- against this table, and the one that must stay an index range scan as row count grows.
CREATE INDEX idx_request_logs_key_created ON request_logs (virtual_key_id, created_at);
