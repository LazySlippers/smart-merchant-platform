CREATE TABLE refund (
 id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, order_id BIGINT NOT NULL, request_id VARCHAR(96) NOT NULL,
 status VARCHAR(24) NOT NULL, amount_cents BIGINT NOT NULL, reason VARCHAR(512), created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), refunded_at TIMESTAMP(3),
 CONSTRAINT uk_refund_request UNIQUE (tenant_id, request_id), CONSTRAINT uk_refund_order UNIQUE (tenant_id, order_id),
 CONSTRAINT fk_refund_order FOREIGN KEY (tenant_id, order_id) REFERENCES orders(tenant_id,id)
);
