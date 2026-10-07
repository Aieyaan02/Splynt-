CREATE TABLE sales_events (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    order_id VARCHAR(64) NOT NULL,
    line_id VARCHAR(64) NOT NULL,
    units NUMERIC(18, 3) NOT NULL CHECK (units > 0),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_sales_line UNIQUE (store_id, order_id, line_id)
);
CREATE INDEX ix_sales_store_date ON sales_events(store_id, occurred_at);
ALTER TABLE clover_oauth_credentials ADD COLUMN sales_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE clover_oauth_credentials ADD COLUMN sales_coverage_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE clover_oauth_credentials ADD COLUMN sales_sync_error VARCHAR(255);
