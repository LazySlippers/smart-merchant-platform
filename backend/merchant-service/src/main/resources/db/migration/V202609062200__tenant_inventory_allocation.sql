ALTER TABLE inventory ADD COLUMN low_stock_threshold BIGINT NOT NULL DEFAULT 5;
ALTER TABLE inventory ADD CONSTRAINT ck_inventory_low_threshold CHECK (low_stock_threshold >= 0);

CREATE TABLE inventory_allocation (
 id BIGINT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 request_id VARCHAR(64) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 reason VARCHAR(512) NOT NULL,
 created_by BIGINT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 CONSTRAINT uk_allocation_request UNIQUE (tenant_id, request_id),
 CONSTRAINT uk_allocation_tenant_id UNIQUE (tenant_id, id)
);
CREATE TABLE inventory_allocation_item (
 tenant_id BIGINT NOT NULL,
 allocation_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 quantity BIGINT NOT NULL,
 PRIMARY KEY (tenant_id, allocation_id, store_id, sku_id),
 CONSTRAINT ck_allocation_quantity CHECK (quantity > 0),
 CONSTRAINT fk_allocation_header FOREIGN KEY (tenant_id, allocation_id) REFERENCES inventory_allocation(tenant_id,id),
 CONSTRAINT fk_allocation_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id,id),
 CONSTRAINT fk_allocation_sku FOREIGN KEY (tenant_id, sku_id) REFERENCES product_sku(tenant_id,id)
);
