-- AI Model Config Table Structure for smart-campus database
-- Create table for storing AI model provider configurations

USE `smart-campus`;

CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `provider` varchar(100) NOT NULL COMMENT '供应商名称 (如：deepseek, openai)',
  `base_url` varchar(500) DEFAULT NULL COMMENT 'API 接口地址',
  `api_key` text COMMENT 'API 密钥（加密存储）',
  `model_name` varchar(100) DEFAULT NULL COMMENT '模型标识 (如：deepseek-chat)',
  `status` int(11) NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用 0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider` (`provider`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置表';
