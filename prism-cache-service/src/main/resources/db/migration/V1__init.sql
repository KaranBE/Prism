CREATE TABLE cache_entries (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_key         VARCHAR(64) NOT NULL,
    model              VARCHAR(64) NOT NULL,
    prompt_text        TEXT NOT NULL,
    embedding          TEXT NOT NULL,
    content            TEXT NOT NULL,
    prompt_tokens      INTEGER NOT NULL,
    completion_tokens  INTEGER NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_cache_entries_tenant_model ON cache_entries (tenant_key, model);
CREATE INDEX idx_cache_entries_expires_at ON cache_entries (expires_at);
