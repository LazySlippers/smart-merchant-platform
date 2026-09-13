ALTER TABLE orders ADD COLUMN member_id BIGINT;
ALTER TABLE orders ADD COLUMN coupon_id BIGINT;
ALTER TABLE orders ADD COLUMN coupon_discount_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN points_used BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN stored_value_used_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN payable_amount_cents BIGINT NOT NULL DEFAULT 0;
UPDATE orders SET payable_amount_cents=total_amount_cents WHERE payable_amount_cents=0;
CREATE INDEX idx_order_member ON orders(tenant_id, member_id, created_at);
