ALTER TABLE orders ADD COLUMN direct_transfer_id BIGINT;
ALTER TABLE orders ADD COLUMN fulfillment_store_id BIGINT;
ALTER TABLE orders ADD CONSTRAINT uk_order_direct_transfer UNIQUE (tenant_id, direct_transfer_id);
