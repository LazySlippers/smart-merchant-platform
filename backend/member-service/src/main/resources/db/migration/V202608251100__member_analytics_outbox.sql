CREATE TABLE analytics_outbox (
  event_id VARCHAR(96) PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(96) NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  published_at TIMESTAMP(3),
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE INDEX idx_member_analytics_outbox_pending ON analytics_outbox(published_at, created_at);
