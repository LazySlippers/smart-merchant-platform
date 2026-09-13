CREATE TABLE user_account (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    status VARCHAR(24) NOT NULL,
    token_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE tenant_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_type VARCHAR(24) NOT NULL,
    data_scope VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    platform_role TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_tenant_role_code UNIQUE (tenant_id, role_code)
);

CREATE TABLE permission (
    id BIGINT PRIMARY KEY,
    permission_code VARCHAR(96) NOT NULL,
    permission_name VARCHAR(96) NOT NULL,
    audience VARCHAR(24) NOT NULL,
    CONSTRAINT uk_permission_code UNIQUE (permission_code)
);

CREATE TABLE user_role (
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, user_id, role_id)
);

CREATE TABLE role_permission (
    tenant_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission_id)
);

CREATE TABLE refresh_token (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);

CREATE TABLE login_audit (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    username VARCHAR(64) NOT NULL,
    result VARCHAR(24) NOT NULL,
    failure_reason VARCHAR(128),
    ip_address VARCHAR(64),
    trace_id VARCHAR(64),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE INDEX idx_tenant_user_lookup ON tenant_user(tenant_id, status, user_id);
CREATE INDEX idx_refresh_user ON refresh_token(tenant_id, user_id, expires_at);
CREATE INDEX idx_login_audit_tenant_time ON login_audit(tenant_id, created_at);

INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2001, 'tenant:application:review', '审核商户申请', 'PLATFORM');
INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2002, 'tenant:tenant:manage', '管理租户', 'PLATFORM');
INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2003, 'tenant:plan:manage', '管理套餐', 'PLATFORM');
INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2004, 'iam:tenant:provision', '创建租户所有者', 'PLATFORM');
INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2101, 'iam:user:manage', '管理员工账号', 'MERCHANT');
INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2102, 'iam:role:manage', '管理角色权限', 'MERCHANT');
INSERT INTO permission (id, permission_code, permission_name, audience) VALUES (2103, 'merchant:dashboard:view', '查看经营概览', 'MERCHANT');
