CREATE TABLE payment_order (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    request_id VARCHAR(96) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_trade_no VARCHAR(96) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount_cents BIGINT NOT NULL,
    checkout_payload VARCHAR(2000),
    expires_at TIMESTAMP(3) NOT NULL,
    succeeded_at TIMESTAMP(3),
    closed_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_payment_order_request UNIQUE (tenant_id, request_id),
    CONSTRAINT uk_payment_order_order UNIQUE (tenant_id, order_id),
    CONSTRAINT uk_payment_order_provider_trade UNIQUE (provider, provider_trade_no),
    CONSTRAINT fk_payment_order_order FOREIGN KEY (tenant_id, order_id) REFERENCES orders(tenant_id, id),
    CONSTRAINT ck_payment_order_amount CHECK (amount_cents >= 0)
);

CREATE TABLE payment_callback_event (
    id BIGINT PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_order_id BIGINT NOT NULL,
    result VARCHAR(24) NOT NULL,
    received_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_payment_callback_event UNIQUE (provider, event_id),
    CONSTRAINT fk_payment_callback_payment FOREIGN KEY (payment_order_id) REFERENCES payment_order(id)
);

CREATE INDEX idx_payment_order_expiry ON payment_order(status, expires_at);
