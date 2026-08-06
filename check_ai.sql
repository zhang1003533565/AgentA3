SELECT config_key, config_value FROM smart_campus.system_config WHERE config_key LIKE 'ai.%' ORDER BY config_key;
SELECT '----leader_agent bindings----';
SELECT config_key, config_value FROM smart_campus.system_config WHERE config_key LIKE 'ai.agent-bindings.leader_agent%';
SELECT '----diagram_mind_map_agent bindings----';
SELECT config_key, config_value FROM smart_campus.system_config WHERE config_key LIKE 'ai.agent-bindings.diagram%';
