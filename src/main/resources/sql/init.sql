-- ============================================
-- 短视频反诈系统 — 数据库初始化 DDL
-- ============================================

CREATE DATABASE IF NOT EXISTS aiscanner
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE aiscanner;

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
