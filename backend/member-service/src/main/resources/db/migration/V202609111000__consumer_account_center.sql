ALTER TABLE unified_consumer_account ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE unified_consumer_address (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    recipient VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(240) NOT NULL,
    label VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unified_address_account FOREIGN KEY (account_id) REFERENCES unified_consumer_account(id)
);
CREATE INDEX idx_unified_address_owner ON unified_consumer_address(account_id, is_default, updated_at);

CREATE TABLE consumer_browse_history (
    id BIGINT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    merchant_name VARCHAR(128) NOT NULL,
    store_name VARCHAR(128) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    sku_name VARCHAR(128) NOT NULL,
    image_url VARCHAR(500) NOT NULL DEFAULT '',
    price_cents BIGINT NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_consumer_browse_item UNIQUE (account_id, tenant_id, store_id, sku_id),
    CONSTRAINT fk_browse_account FOREIGN KEY (account_id) REFERENCES unified_consumer_account(id),
    CONSTRAINT ck_browse_price CHECK (price_cents >= 0)
);
CREATE INDEX idx_consumer_browse_owner_time ON consumer_browse_history(account_id, viewed_at);
