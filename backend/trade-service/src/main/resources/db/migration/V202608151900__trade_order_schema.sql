CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    store_id BIGINT NOT NULL,
    customer_name VARCHAR(64) NOT NULL,
    customer_mobile VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_quantity BIGINT NOT NULL,
    total_amount_cents BIGINT NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    paid_at TIMESTAMP(3),
    pickup_code VARCHAR(32),
    verified_at TIMESTAMP(3),
    closed_at TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_order_no UNIQUE (tenant_id, order_no),
    CONSTRAINT uk_order_request UNIQUE (tenant_id, request_id),
    CONSTRAINT uk_order_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_order_pickup_code UNIQUE (tenant_id, pickup_code),
    CONSTRAINT ck_order_quantity_positive CHECK (total_quantity > 0),
    CONSTRAINT ck_order_amount_nonnegative CHECK (total_amount_cents >= 0)
);

CREATE TABLE order_item (
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    sku_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    product_name_snapshot VARCHAR(160) NOT NULL,
    sku_name_snapshot VARCHAR(160) NOT NULL,
    spec_snapshot VARCHAR(1000),
    image_url_snapshot VARCHAR(512),
    unit_price_cents BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    line_amount_cents BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, order_id, line_no),
    CONSTRAINT fk_order_item_order FOREIGN KEY (tenant_id, order_id) REFERENCES orders(tenant_id, id),
    CONSTRAINT ck_order_item_price_nonnegative CHECK (unit_price_cents >= 0),
    CONSTRAINT ck_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_item_amount_nonnegative CHECK (line_amount_cents >= 0)
);

CREATE TABLE payment (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    payment_request_id VARCHAR(96) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount_cents BIGINT NOT NULL,
    paid_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_payment_request UNIQUE (tenant_id, payment_request_id),
    CONSTRAINT uk_payment_order UNIQUE (tenant_id, order_id),
    CONSTRAINT fk_payment_order FOREIGN KEY (tenant_id, order_id) REFERENCES orders(tenant_id, id)
);

CREATE TABLE pickup_verification (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    pickup_code VARCHAR(32) NOT NULL,
    verifier_user_id BIGINT NOT NULL,
    verified_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_pickup_order UNIQUE (tenant_id, order_id),
    CONSTRAINT fk_pickup_order FOREIGN KEY (tenant_id, order_id) REFERENCES orders(tenant_id, id)
);

CREATE TABLE finance_ledger (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    amount_cents BIGINT NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    remark VARCHAR(512),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_finance_business UNIQUE (tenant_id, business_type, business_id),
    CONSTRAINT ck_finance_amount_nonnegative CHECK (amount_cents >= 0)
);

CREATE INDEX idx_order_tenant_store_status ON orders(tenant_id, store_id, status, created_at);
CREATE INDEX idx_order_expiry ON orders(status, expires_at);
CREATE INDEX idx_finance_store_time ON finance_ledger(tenant_id, store_id, occurred_at);
