CREATE TABLE analytics_inbox (
  event_id VARCHAR(96) PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_version INT NOT NULL,
  aggregate_id VARCHAR(96) NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL,
  payload_json LONGTEXT,
  received_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE INDEX idx_analytics_inbox_tenant_time ON analytics_inbox(tenant_id, occurred_at);

CREATE TABLE analytics_order_fact (
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  member_id BIGINT,
  paid_at TIMESTAMP(3) NOT NULL,
  paid_amount_cents BIGINT NOT NULL,
  refund_amount_cents BIGINT NOT NULL DEFAULT 0,
  coupon_discount_cents BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, order_id)
);
CREATE INDEX idx_analytics_order_store_time ON analytics_order_fact(tenant_id, store_id, paid_at);

CREATE TABLE analytics_order_item_fact (
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  store_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  product_name VARCHAR(160) NOT NULL,
  category_name VARCHAR(160),
  quantity BIGINT NOT NULL,
  amount_cents BIGINT NOT NULL,
  paid_at TIMESTAMP(3) NOT NULL,
  PRIMARY KEY (tenant_id, order_id, line_no)
);
CREATE INDEX idx_analytics_item_rank ON analytics_order_item_fact(tenant_id, store_id, paid_at);

CREATE TABLE analytics_member_event_fact (
  event_id VARCHAR(96) PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  member_id BIGINT,
  event_type VARCHAR(64) NOT NULL,
  points_delta BIGINT NOT NULL DEFAULT 0,
  stored_value_delta_cents BIGINT NOT NULL DEFAULT 0,
  occurred_at TIMESTAMP(3) NOT NULL
);
CREATE INDEX idx_analytics_member_time ON analytics_member_event_fact(tenant_id, occurred_at);

CREATE TABLE analytics_metric_minute (
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  metric_minute TIMESTAMP(0) NOT NULL,
  paid_order_count BIGINT NOT NULL DEFAULT 0,
  gross_revenue_cents BIGINT NOT NULL DEFAULT 0,
  refund_cents BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, store_id, metric_minute)
);
CREATE TABLE analytics_metric_daily (
  tenant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  metric_date DATE NOT NULL,
  paid_order_count BIGINT NOT NULL DEFAULT 0,
  gross_revenue_cents BIGINT NOT NULL DEFAULT 0,
  refund_cents BIGINT NOT NULL DEFAULT 0,
  reconciled_at TIMESTAMP(3),
  PRIMARY KEY (tenant_id, store_id, metric_date)
);
