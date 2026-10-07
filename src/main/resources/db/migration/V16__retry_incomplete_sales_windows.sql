ALTER TABLE clover_oauth_credentials ADD COLUMN sales_retry_from TIMESTAMP WITH TIME ZONE;
-- Reconcile previously imported windows once: older incremental imports could clear
-- warnings without retrying omitted paid lines. Subsequent clean imports clear this cursor.
UPDATE clover_oauth_credentials SET sales_retry_from = sales_coverage_start
WHERE sales_coverage_start IS NOT NULL;
