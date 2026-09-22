ALTER TABLE products
    ADD COLUMN store_id BIGINT;

UPDATE products
SET store_id = (
    SELECT s.id
    FROM stores s
    JOIN organizations o
        ON o.id = s.organization_id
    WHERE o.slug = 'splynt-demo'
      AND s.slug = 'demo-store'
)
WHERE store_id IS NULL;

ALTER TABLE products
    ADD CONSTRAINT fk_products_store
    FOREIGN KEY (store_id)
    REFERENCES stores(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_products_store_active_name
    ON products(store_id, active, name);

CREATE INDEX idx_products_store_quantity
    ON products(store_id, quantity);