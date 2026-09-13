CREATE TABLE inventory (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    actual_quantity BIGINT NOT NULL DEFAULT 0,
    available_quantity BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_inventory_store_sku UNIQUE (tenant_id, store_id, sku_id),
    CONSTRAINT ck_inventory_actual CHECK (actual_quantity >= 0),
    CONSTRAINT ck_inventory_available CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_balance CHECK (actual_quantity = available_quantity + reserved_quantity),
    CONSTRAINT fk_inventory_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id, id),
    CONSTRAINT fk_inventory_sku FOREIGN KEY (tenant_id, sku_id) REFERENCES product_sku(tenant_id, id)
);

CREATE TABLE inventory_ledger (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    quantity_delta BIGINT NOT NULL,
    available_delta BIGINT NOT NULL,
    reserved_delta BIGINT NOT NULL,
    quantity_after BIGINT NOT NULL,
    available_after BIGINT NOT NULL,
    reserved_after BIGINT NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id BIGINT,
    idempotency_key VARCHAR(96) NOT NULL,
    reason VARCHAR(512),
    operator_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_inventory_ledger_idempotency UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE stock_count (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    count_no VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL,
    completed_by BIGINT,
    completed_at TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_stock_count_no UNIQUE (tenant_id, count_no),
    CONSTRAINT uk_stock_count_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_stock_count_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id, id)
);

CREATE TABLE stock_count_item (
    tenant_id BIGINT NOT NULL,
    count_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    expected_quantity BIGINT NOT NULL,
    counted_quantity BIGINT NOT NULL,
    difference_quantity BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, count_id, sku_id),
    CONSTRAINT ck_stock_counted_nonnegative CHECK (counted_quantity >= 0),
    CONSTRAINT fk_count_item_count FOREIGN KEY (tenant_id, count_id) REFERENCES stock_count(tenant_id, id),
    CONSTRAINT fk_count_item_sku FOREIGN KEY (tenant_id, sku_id) REFERENCES product_sku(tenant_id, id)
);

CREATE TABLE stock_transfer (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    transfer_no VARCHAR(64) NOT NULL,
    source_store_id BIGINT NOT NULL,
    target_store_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    remark VARCHAR(512),
    created_by BIGINT NOT NULL,
    approved_by BIGINT,
    shipped_by BIGINT,
    received_by BIGINT,
    approved_at TIMESTAMP(3),
    shipped_at TIMESTAMP(3),
    received_at TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_transfer_no UNIQUE (tenant_id, transfer_no),
    CONSTRAINT uk_transfer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_transfer_different_store CHECK (source_store_id <> target_store_id),
    CONSTRAINT fk_transfer_source FOREIGN KEY (tenant_id, source_store_id) REFERENCES store(tenant_id, id),
    CONSTRAINT fk_transfer_target FOREIGN KEY (tenant_id, target_store_id) REFERENCES store(tenant_id, id)
);

CREATE TABLE stock_transfer_item (
    tenant_id BIGINT NOT NULL,
    transfer_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, transfer_id, sku_id),
    CONSTRAINT ck_transfer_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_transfer_item_transfer FOREIGN KEY (tenant_id, transfer_id) REFERENCES stock_transfer(tenant_id, id),
    CONSTRAINT fk_transfer_item_sku FOREIGN KEY (tenant_id, sku_id) REFERENCES product_sku(tenant_id, id)
);

CREATE INDEX idx_inventory_store ON inventory(tenant_id, store_id, sku_id);
CREATE INDEX idx_inventory_ledger_store_time ON inventory_ledger(tenant_id, store_id, created_at);
CREATE INDEX idx_stock_count_store_status ON stock_count(tenant_id, store_id, status);
CREATE INDEX idx_transfer_source_status ON stock_transfer(tenant_id, source_store_id, status);
CREATE INDEX idx_transfer_target_status ON stock_transfer(tenant_id, target_store_id, status);
