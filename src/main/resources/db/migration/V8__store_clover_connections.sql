-- Legacy credentials remain unassigned until a merchant explicitly authorizes a store.
ALTER TABLE clover_oauth_credentials ADD COLUMN store_id BIGINT REFERENCES stores(id);
ALTER TABLE clover_oauth_credentials ADD CONSTRAINT uq_clover_store UNIQUE (store_id);
ALTER TABLE clover_oauth_credentials ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE clover_oauth_credentials ADD COLUMN last_sync_error VARCHAR(255);
CREATE TABLE clover_oauth_attempts (
    state_hash VARCHAR(64) PRIMARY KEY,
    browser_hash VARCHAR(64) NOT NULL,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_clover_oauth_expiry ON clover_oauth_attempts(expires_at);
