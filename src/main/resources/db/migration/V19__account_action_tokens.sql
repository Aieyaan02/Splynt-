CREATE TABLE account_action_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    purpose VARCHAR(24) NOT NULL,
    email VARCHAR(255) NOT NULL,
    credential_version BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_account_action_user_purpose UNIQUE (user_id, purpose)
);
CREATE INDEX ix_account_action_expiry ON account_action_tokens(expires_at);
