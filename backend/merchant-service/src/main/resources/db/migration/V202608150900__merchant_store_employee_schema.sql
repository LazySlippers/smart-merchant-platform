CREATE TABLE store (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    store_code VARCHAR(64) NOT NULL,
    store_name VARCHAR(128) NOT NULL,
    address VARCHAR(512),
    business_hours VARCHAR(128),
    status VARCHAR(24) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_store_tenant_code UNIQUE (tenant_id, store_code),
    CONSTRAINT uk_store_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE employee_profile (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    employee_no VARCHAR(64) NOT NULL,
    employee_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    primary_store_id BIGINT,
    job_title VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_employee_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT uk_employee_tenant_no UNIQUE (tenant_id, employee_no),
    CONSTRAINT uk_employee_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_employee_primary_store FOREIGN KEY (tenant_id, primary_store_id) REFERENCES store(tenant_id, id)
);

CREATE TABLE employee_store (
    tenant_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, employee_id, store_id),
    CONSTRAINT fk_employee_store_employee FOREIGN KEY (tenant_id, employee_id) REFERENCES employee_profile(tenant_id, id),
    CONSTRAINT fk_employee_store_store FOREIGN KEY (tenant_id, store_id) REFERENCES store(tenant_id, id)
);

CREATE INDEX idx_store_tenant_status ON store(tenant_id, status, id);
CREATE INDEX idx_employee_tenant_status ON employee_profile(tenant_id, status, id);
CREATE INDEX idx_employee_store_lookup ON employee_store(tenant_id, store_id, employee_id);
