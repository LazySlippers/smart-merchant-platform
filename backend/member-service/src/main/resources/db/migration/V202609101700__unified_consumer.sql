-- Legacy credentials and assets remain untouched. Linking requires the legacy password.
CREATE TABLE unified_consumer_account (
    id BIGINT PRIMARY KEY,
    mobile VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE member ADD CONSTRAINT uk_member_tenant_identity UNIQUE (tenant_id, id);
CREATE TABLE consumer_membership (
    account_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, tenant_id),
    CONSTRAINT uk_membership_owner UNIQUE (tenant_id, member_id),
    CONSTRAINT fk_membership_account FOREIGN KEY (account_id) REFERENCES unified_consumer_account(id),
    CONSTRAINT fk_membership_member FOREIGN KEY (tenant_id, member_id) REFERENCES member(tenant_id, id)
);
