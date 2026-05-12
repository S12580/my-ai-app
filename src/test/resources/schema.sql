-- H2 (MODE=MySQL)：与主库 schema_mysql.sql 一致；无外键，仅索引（应用层保证引用完整性）
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;

CREATE TABLE chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL DEFAULT '',
  model VARCHAR(64) NOT NULL DEFAULT 'qwen-plus',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content TEXT NOT NULL,
  meta_json VARCHAR(4000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_session_updated ON chat_session(updated_at);
CREATE INDEX idx_chat_message_session_created ON chat_message(session_id, created_at);
CREATE INDEX idx_chat_message_session_id ON chat_message(session_id, id);
