CREATE TABLE unified_consumer_session (
    id VARCHAR(36) PRIMARY KEY,
    account_id BIGINT NOT NULL,
    last_used_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (account_id) REFERENCES unified_consumer_account(id) ON DELETE CASCADE
);
CREATE INDEX idx_consumer_session_owner ON unified_consumer_session(account_id);
ALTER TABLE unified_consumer_account ADD COLUMN history_enabled BOOLEAN NOT NULL DEFAULT TRUE;
