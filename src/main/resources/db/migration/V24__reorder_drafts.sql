CREATE TABLE reorder_drafts (
    id uuid PRIMARY KEY,
    store_id bigint NOT NULL REFERENCES stores(id),
    version bigint NOT NULL DEFAULT 0,
    supplier varchar(150) NOT NULL,
    supplier_contact varchar(255) NOT NULL,
    notes varchar(1000) NOT NULL,
    items_json text NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('DRAFT', 'ORDERED')),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    ordered_at timestamp with time zone
);
CREATE INDEX idx_reorder_store_created ON reorder_drafts(store_id, created_at DESC);
