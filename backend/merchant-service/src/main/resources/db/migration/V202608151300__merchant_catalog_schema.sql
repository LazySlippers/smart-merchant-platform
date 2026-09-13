CREATE TABLE category (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    parent_id BIGINT,
    category_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_category_tenant_code UNIQUE (tenant_id, category_code),
    CONSTRAINT uk_category_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_category_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES category(tenant_id, id)
);

CREATE TABLE product_spu (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    spu_code VARCHAR(64) NOT NULL,
    product_name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    image_url VARCHAR(512),
    status VARCHAR(24) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_spu_tenant_code UNIQUE (tenant_id, spu_code),
    CONSTRAINT uk_spu_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_spu_category FOREIGN KEY (tenant_id, category_id) REFERENCES category(tenant_id, id)
);

CREATE TABLE product_sku (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    sku_name VARCHAR(160) NOT NULL,
    spec_json VARCHAR(1000),
    barcode VARCHAR(64),
    base_price_cents BIGINT NOT NULL,
    allow_store_price TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_sku_price_nonnegative CHECK (base_price_cents >= 0),
    CONSTRAINT uk_sku_tenant_code UNIQUE (tenant_id, sku_code),
    CONSTRAINT uk_sku_tenant_barcode UNIQUE (tenant_id, barcode),
    CONSTRAINT uk_sku_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_sku_spu FOREIGN KEY (tenant_id, spu_id) REFERENCES product_spu(tenant_id, id)
);

CREATE TABLE store_product (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    sellable TINYINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, store_id, sku_id),
    CONSTRAINT fk_store_product_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id, id),
    CONSTRAINT fk_store_product_sku FOREIGN KEY (tenant_id, sku_id) REFERENCES product_sku(tenant_id, id)
);

CREATE TABLE store_price (
    tenant_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    price_cents BIGINT NOT NULL,
    approved_by BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, store_id, sku_id),
    CONSTRAINT ck_store_price_nonnegative CHECK (price_cents >= 0),
    CONSTRAINT fk_store_price_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id, id),
    CONSTRAINT fk_store_price_sku FOREIGN KEY (tenant_id, sku_id) REFERENCES product_sku(tenant_id, id)
);

CREATE INDEX idx_category_tenant_status ON category(tenant_id, status, sort_order);
CREATE INDEX idx_spu_tenant_category ON product_spu(tenant_id, category_id, status);
CREATE INDEX idx_sku_tenant_spu ON product_sku(tenant_id, spu_id, status);
CREATE INDEX idx_store_product_sellable ON store_product(tenant_id, store_id, sellable, sku_id);
