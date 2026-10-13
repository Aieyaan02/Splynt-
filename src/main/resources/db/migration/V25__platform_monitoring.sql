CREATE TABLE platform_admin (
    singleton integer PRIMARY KEY CHECK (singleton = 1),
    user_id bigint NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE RESTRICT
);
ALTER TABLE app_users ADD COLUMN last_activity_at timestamp with time zone;
ALTER TABLE clover_oauth_credentials ADD COLUMN last_sync_failed_at timestamp with time zone;
ALTER TABLE clover_oauth_credentials ADD COLUMN last_sync_received_count integer;
ALTER TABLE clover_oauth_credentials ADD COLUMN last_sync_imported_count integer;
CREATE INDEX idx_users_registered ON app_users(created_at, id);
CREATE INDEX idx_organizations_registered ON organizations(created_at, id);
