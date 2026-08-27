-- =============================================
-- Bind Resume Agents with AI Model Config
-- Date: 2026-08-24
-- =============================================

USE `smart-campus`;

-- 插入 resume_create_agent 绑定（AI 生成简历）
INSERT INTO `agent_model_bind` (`agent_id`, `model_config_id`)
VALUES ('resume_create_agent', 1)
ON DUPLICATE KEY UPDATE 
    model_config_id = VALUES(model_config_id),
    update_time = NOW();

-- 插入 resume_edit_agent 绑定（AI 一键改简历）
INSERT INTO `agent_model_bind` (`agent_id`, `model_config_id`)
VALUES ('resume_edit_agent', 1)
ON DUPLICATE KEY UPDATE 
    model_config_id = VALUES(model_config_id),
    update_time = NOW();

-- 验证绑定结果
SELECT 
    amb.agent_id,
    amb.model_config_id,
    amc.provider,
    amc.model_name,
    amc.status,
    amb.create_time
FROM agent_model_bind amb
LEFT JOIN ai_model_config amc ON amb.model_config_id = amc.id
ORDER BY amb.create_time DESC;

-- 显示所有已绑定的智能体
SELECT '✅ 成功绑定以下智能体与模型配置' AS message;
