CREATE TABLE member (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    mobile VARCHAR(32) NOT NULL,
    member_name VARCHAR(64) NOT NULL,
    level_code VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_member_mobile UNIQUE (tenant_id, mobile)
);

CREATE TABLE member_level (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    level_code VARCHAR(32) NOT NULL,
    level_name VARCHAR(64) NOT NULL,
    min_points BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_member_level UNIQUE (tenant_id, level_code),
    CONSTRAINT ck_member_level_points CHECK (min_points >= 0)
);

CREATE TABLE points_account (
    member_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    available_points BIGINT NOT NULL DEFAULT 0,
    frozen_points BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_points_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT ck_points_nonnegative CHECK (available_points >= 0 AND frozen_points >= 0)
);

CREATE TABLE points_ledger (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    points_delta BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_points_ledger UNIQUE (tenant_id, business_key, action),
    CONSTRAINT fk_points_ledger_member FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE TABLE stored_value_account (
    member_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    available_cents BIGINT NOT NULL DEFAULT 0,
    frozen_cents BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stored_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT ck_stored_nonnegative CHECK (available_cents >= 0 AND frozen_cents >= 0)
);

CREATE TABLE stored_value_ledger (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL,
    amount_delta_cents BIGINT NOT NULL,
    balance_after_cents BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_stored_ledger UNIQUE (tenant_id, business_key, action),
    CONSTRAINT fk_stored_ledger_member FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE TABLE coupon_template (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    discount_cents BIGINT NOT NULL,
    min_spend_cents BIGINT NOT NULL DEFAULT 0,
    total_quantity BIGINT NOT NULL,
    claimed_quantity BIGINT NOT NULL DEFAULT 0,
    valid_from TIMESTAMP(3) NOT NULL,
    valid_to TIMESTAMP(3) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_coupon_template_code UNIQUE (tenant_id, template_code),
    CONSTRAINT ck_coupon_quantity CHECK (total_quantity > 0 AND claimed_quantity >= 0 AND claimed_quantity <= total_quantity),
    CONSTRAINT ck_coupon_discount CHECK (discount_cents > 0 AND min_spend_cents >= 0)
);

CREATE TABLE member_coupon (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    frozen_order_id BIGINT,
    claimed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    used_at TIMESTAMP(3),
    expires_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT uk_member_coupon UNIQUE (tenant_id, member_id, template_id),
    CONSTRAINT fk_member_coupon_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT fk_member_coupon_template FOREIGN KEY (template_id) REFERENCES coupon_template(id)
);

CREATE TABLE benefit_ledger (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    order_id BIGINT,
    benefit_type VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    amount_cents BIGINT NOT NULL DEFAULT 0,
    business_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_benefit_ledger UNIQUE (tenant_id, business_key, benefit_type, action),
    CONSTRAINT fk_benefit_ledger_member FOREIGN KEY (member_id) REFERENCES member(id)
);

CREATE INDEX idx_member_tenant_mobile ON member(tenant_id, mobile);
CREATE INDEX idx_coupon_claim ON member_coupon(tenant_id, member_id, status);
