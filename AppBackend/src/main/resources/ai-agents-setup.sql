-- =============================================
-- AI Model Config and Agent Bind Database Setup
-- Date: 2026-08-24
-- =============================================

USE `smart-campus`;

-- 1. 创建 AI 模型配置表（如果不存在）
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `provider` varchar(100) NOT NULL COMMENT '供应商名称（如：deepseek, openai）',
  `base_url` varchar(500) DEFAULT NULL COMMENT 'API 接口地址',
  `api_key` text COMMENT 'API 密钥（加密存储）',
  `model_name` varchar(100) DEFAULT NULL COMMENT '模型标识（如：deepseek-chat）',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用 0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider` (`provider`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置表';

-- 2. 创建智能体 - 模型绑定表（如果不存在）
CREATE TABLE IF NOT EXISTS `agent_model_bind` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` varchar(50) NOT NULL COMMENT '智能体 ID（如：resume-editor, resume-generator）',
  `model_config_id` bigint(20) NOT NULL COMMENT 'AI 模型配置 ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent` (`agent_id`) USING BTREE COMMENT '一个智能体只能绑定一套模型'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体 - 模型绑定表';

-- 3. 插入初始 DeepSeek 模型配置（示例数据）
INSERT INTO `ai_model_config` 
    (`id`, `provider`, `base_url`, `api_key`, `model_name`, `status`, `create_time`, `update_time`)
VALUES 
    (1, 'deepseek', 'http://127.0.0.1:8081', NULL, 'deepseek-chat', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    provider = VALUES(provider),
    base_url = VALUES(base_url),
    model_name = VALUES(model_name),
    status = VALUES(status),
    update_time = VALUES(update_time);

-- 4. 验证表结构
SELECT 'Table ai_model_config created successfully!' AS message;
SHOW TABLES LIKE 'ai_model_config';

SELECT 'Table agent_model_bind created successfully!' AS message;
SHOW TABLES LIKE 'agent_model_bind';
