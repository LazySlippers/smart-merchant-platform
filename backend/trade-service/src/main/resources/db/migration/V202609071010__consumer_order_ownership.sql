ALTER TABLE orders ADD COLUMN consumer_member_id BIGINT NULL;
CREATE INDEX idx_orders_consumer ON orders(tenant_id,consumer_member_id,created_at);
