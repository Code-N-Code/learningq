CREATE TABLE auth_tokens (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    purpose VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    CONSTRAINT pk_auth_tokens PRIMARY KEY (id),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('email_verification', 'password_reset')),
    INDEX ix_auth_tokens_user_purpose (user_id, purpose),
    INDEX ix_auth_tokens_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
