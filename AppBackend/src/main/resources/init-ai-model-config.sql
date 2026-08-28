-- Insert default record for ai_model_config table
USE `smart-campus`;

INSERT INTO `ai_model_config` 
    (`id`, `provider`, `base_url`, `api_key`, `model_name`, `status`, `create_time`, `update_time`)
VALUES 
    (1, 'deepseek', 'https://api.deepseek.com', NULL, 'deepseek-chat', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    provider = VALUES(provider),
    base_url = VALUES(base_url),
    model_name = VALUES(model_name),
    status = VALUES(status),
    update_time = VALUES(update_time);
