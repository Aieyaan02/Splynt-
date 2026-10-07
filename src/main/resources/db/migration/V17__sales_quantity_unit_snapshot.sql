ALTER TABLE sales_events ADD COLUMN quantity_unit VARCHAR(64);
-- Existing rows remain unknown rather than borrowing the current catalog unit.
-- Re-read provider order snapshots to recover units where historical data is available.
UPDATE clover_oauth_credentials SET sales_retry_from = sales_coverage_start
WHERE sales_coverage_start IS NOT NULL;
