CREATE TABLE IF NOT EXISTS t_blacklist (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    author_id   VARCHAR(128)    NOT NULL,
    list_type   VARCHAR(16)     NOT NULL COMMENT 'AUTHORITY or GLOBAL',
    reason      VARCHAR(255)    DEFAULT '',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_author_list (author_id, list_type),
    INDEX idx_list_type (list_type),
    INDEX idx_author_id (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
