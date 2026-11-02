ALTER TABLE clover_oauth_credentials ADD COLUMN authorized_at TIMESTAMP WITH TIME ZONE;
UPDATE clover_oauth_credentials SET authorized_at = created_at;
