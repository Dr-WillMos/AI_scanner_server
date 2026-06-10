-- ============================================
-- 短视频反诈系统 — 数据库初始化 DDL
-- Flyway 管理的首个迁移版本
-- ============================================

-- 检测记录表
CREATE TABLE IF NOT EXISTS t_detection_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    device_id       VARCHAR(128) NOT NULL                 COMMENT '设备ID',
    author_id       VARCHAR(128) NOT NULL                 COMMENT '抖音作者号',
    risk_level      VARCHAR(16)  NOT NULL                 COMMENT '风险等级: HIGH / MEDIUM / SAFE',
    score           DOUBLE       NOT NULL DEFAULT 0.0     COMMENT '风险加权分数',
    raw_ai_result   TEXT                                  COMMENT 'AI 服务原始返回 JSON',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    INDEX idx_device_id (device_id),
    INDEX idx_author_id (author_id),
    INDEX idx_risk_level (risk_level),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检测记录表';

-- API Key 管理表
CREATE TABLE IF NOT EXISTS t_api_key (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    key_value       VARCHAR(64)  NOT NULL                 COMMENT 'API Key 值（UUID 去横线）',
    key_name        VARCHAR(128) NOT NULL                 COMMENT 'Key 描述（设备名/用途）',
    device_id       VARCHAR(128)                          COMMENT '绑定的设备 ID（可空）',
    permissions     VARCHAR(256) NOT NULL DEFAULT 'DETECT,HISTORY'
                                                          COMMENT '权限列表，逗号分隔',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / REVOKED',
    rate_limit      INT          NOT NULL DEFAULT 20      COMMENT '每分钟最大请求数',
    last_used_at    DATETIME                              COMMENT '最后使用时间',
    expired_at      DATETIME                              COMMENT '过期时间（可空 = 永不过期）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at      DATETIME                              COMMENT '吊销时间',

    PRIMARY KEY (id),
    UNIQUE INDEX uk_key_value (key_value),
    INDEX idx_device_id (device_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API Key 管理表';
