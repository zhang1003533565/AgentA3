-- AI Model Bind Table Structure for smart-campus database
-- Create table for binding agents with model configurations

USE `smart-campus`;

CREATE TABLE IF NOT EXISTS `agent_model_bind` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `agent_id` varchar(50) NOT NULL COMMENT '智能体 ID（如：resume-editor, resume-generator）',
  `model_config_id` bigint(20) NOT NULL COMMENT 'AI 模型配置 ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent` (`agent_id`) USING BTREE COMMENT '一个智能体只能绑定一套模型'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能体 - 模型绑定表';
