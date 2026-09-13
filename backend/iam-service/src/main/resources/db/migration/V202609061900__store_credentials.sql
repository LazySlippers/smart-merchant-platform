CREATE TABLE store_credential (
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 encrypted_password VARCHAR(2048) NOT NULL,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY (tenant_id,user_id)
);
CREATE TABLE store_credential_audit (
 id BIGINT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
