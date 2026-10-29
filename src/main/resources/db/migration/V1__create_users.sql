CREATE TABLE users (
                       id BINARY(16) NOT NULL,

                       email VARCHAR(254) NOT NULL,
                       email_normalized VARCHAR(254)
                              CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,

                       password_hash VARCHAR(255) NOT NULL,
                       display_name VARCHAR(80) NULL,

                       email_verified_at DATETIME(6) NULL,
                       auth_version BIGINT NOT NULL DEFAULT 0,

                       created_at DATETIME(6) NOT NULL,
                       updated_at DATETIME(6) NOT NULL,

                       CONSTRAINT pk_users PRIMARY KEY (id),
                       CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized)
)
    ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;