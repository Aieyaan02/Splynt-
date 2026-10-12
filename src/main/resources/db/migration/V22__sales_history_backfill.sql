ALTER TABLE clover_oauth_credentials ADD COLUMN sales_backfill_target TIMESTAMP WITH TIME ZONE;
ALTER TABLE clover_oauth_credentials ADD COLUMN sales_backfill_error VARCHAR(255);
