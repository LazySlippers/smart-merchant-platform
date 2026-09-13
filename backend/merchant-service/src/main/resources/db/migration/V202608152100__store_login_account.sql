CREATE TABLE store_login_account (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, store_id),
    CONSTRAINT uk_store_login_user UNIQUE (tenant_id, user_id),
    CONSTRAINT uk_store_login_username UNIQUE (tenant_id, username),
    CONSTRAINT fk_store_login_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id, id)
);
