-- MySQL 5.7 utf8mb4 — run once against your server before starting the app.

CREATE DATABASE IF NOT EXISTS my_ai_app
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE my_ai_app;

-- 使用秒级 DATETIME + CURRENT_TIMESTAMP，避免部分 MySQL 5.7 在 DATETIME(3)/CURRENT_TIMESTAMP(3) 默认值上报错 1067
-- 未建外键：由应用层保证 session_id 有效；保留 session_id 上的普通索引供查询
CREATE TABLE IF NOT EXISTS chat_session (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title         VARCHAR(255)    NOT NULL DEFAULT '',
  model         VARCHAR(64)     NOT NULL DEFAULT 'qwen-plus',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_chat_session_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_message (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  session_id    BIGINT UNSIGNED NOT NULL,
  role          VARCHAR(16)     NOT NULL COMMENT 'user|assistant|system',
  content       TEXT            NOT NULL,
  meta_json     JSON            NULL COMMENT 'optional: error, finish_reason',
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_message_session_created (session_id, created_at),
  KEY idx_chat_message_session_id (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
