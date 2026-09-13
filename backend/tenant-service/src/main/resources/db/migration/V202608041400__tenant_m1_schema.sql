CREATE TABLE tenant_application (
    id BIGINT PRIMARY KEY,
    merchant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NOT NULL,
    contact_mobile VARCHAR(32) NOT NULL,
    requested_plan_code VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    review_reason VARCHAR(512),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE tenant (
    id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(32) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    owner_name VARCHAR(64) NOT NULL,
    owner_mobile VARCHAR(32) NOT NULL,
    source_application_id BIGINT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_tenant_code UNIQUE (tenant_code),
    CONSTRAINT uk_tenant_application UNIQUE (source_application_id)
);

CREATE TABLE plan (
    id BIGINT PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_plan_code UNIQUE (plan_code)
);

CREATE TABLE plan_feature (
    id BIGINT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    feature_code VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL,
    quota_value BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_plan_feature UNIQUE (plan_id, feature_code)
);

CREATE TABLE tenant_subscription (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    starts_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_tenant_subscription UNIQUE (tenant_id)
);

CREATE TABLE tenant_audit_log (
    id BIGINT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    detail VARCHAR(1000),
    trace_id VARCHAR(64),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_application_status_created ON tenant_application(status, created_at);
CREATE INDEX idx_tenant_status_created ON tenant(status, created_at);
CREATE INDEX idx_subscription_plan ON tenant_subscription(plan_id, status);

INSERT INTO plan (id, plan_code, plan_name, status) VALUES (1001, 'STANDARD', '标准版', 'ACTIVE');
INSERT INTO plan (id, plan_code, plan_name, status) VALUES (1002, 'ADVANCED', '高级版', 'ACTIVE');
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1101, 1001, 'merchant.store', 1, 3);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1102, 1001, 'merchant.employee', 1, 20);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1103, 1001, 'marketing.coupon', 1, 20);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1104, 1001, 'marketing.stored_value', 0, 0);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1201, 1002, 'merchant.store', 1, 50);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1202, 1002, 'merchant.employee', 1, 500);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1203, 1002, 'marketing.coupon', 1, 500);
INSERT INTO plan_feature (id, plan_id, feature_code, enabled, quota_value) VALUES (1204, 1002, 'marketing.stored_value', 1, NULL);
