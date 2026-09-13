CREATE TABLE consumer_preferences (
 tenant_id BIGINT NOT NULL, member_id BIGINT NOT NULL, notifications BOOLEAN NOT NULL DEFAULT TRUE,
 PRIMARY KEY (tenant_id, member_id)
);
CREATE TABLE consumer_address (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 recipient VARCHAR(40) NOT NULL, phone VARCHAR(20) NOT NULL, address VARCHAR(240) NOT NULL,
 label VARCHAR(20) NOT NULL, is_default BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_consumer_address_owner ON consumer_address (tenant_id, member_id);
CREATE TABLE consumer_favorite (
 tenant_id BIGINT NOT NULL, member_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (tenant_id, member_id, sku_id)
);
CREATE TABLE consumer_feedback (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 content VARCHAR(1000) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_consumer_feedback_owner ON consumer_feedback (tenant_id, member_id);
