CREATE TABLE usage_records (
    id                 BIGSERIAL PRIMARY KEY,
    virtual_key_id     BIGINT NOT NULL,
    requested_model    VARCHAR(64),
    resolved_model     VARCHAR(64),
    provider           VARCHAR(64),
    status             VARCHAR(32),
    prompt_tokens      INTEGER,
    completion_tokens  INTEGER,
    cost_usd           NUMERIC(12,6),
    cache_hit          BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_used      BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms         BIGINT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_usage_records_key_created ON usage_records (virtual_key_id, created_at);
CREATE INDEX idx_usage_records_provider_created ON usage_records (provider, created_at);
