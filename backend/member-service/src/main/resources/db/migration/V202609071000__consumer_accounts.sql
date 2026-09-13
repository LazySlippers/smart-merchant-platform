CREATE TABLE consumer_account (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_consumer_mobile UNIQUE (tenant_id, mobile),
    CONSTRAINT uk_consumer_member UNIQUE (tenant_id, member_id)
);
