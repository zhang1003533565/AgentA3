package com.example.appbackend.service.impl;

import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.SystemConfigService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final Set<String> ENVIRONMENT_ONLY_KEYS = Set.of(
            "jwt.secret",
            "tencent.map.key",
            "tencent.cos.secret-id",
            "tencent.cos.secret-key",
            "amap.app.key",
            "amap.web.key",
            "aliyun.oss.access-key-id",
            "aliyun.oss.access-key-secret"
    );

    private final SystemConfigRepository systemConfigRepository;
    private final Environment environment;

    public SystemConfigServiceImpl(SystemConfigRepository systemConfigRepository, Environment environment) {
        this.systemConfigRepository = systemConfigRepository;
        this.environment = environment;
    }

    @Override
    public String getValue(String key, String defaultValue) {
        String localLlmValue = getLocalLlmTextConfigValue(key);
        if (localLlmValue != null) {
            return localLlmValue;
        }
        if (ENVIRONMENT_ONLY_KEYS.contains(key)) {
            String value = environment.getProperty(key);
            return value == null || value.isBlank() ? defaultValue : value;
        }
        return systemConfigRepository.findByConfigKeyAndStatus(key, 1)
                .map(item -> item.getConfigValue())
                .filter(value -> value != null && !value.isBlank())
                .orElse(defaultValue);
    }

    private String getLocalLlmTextConfigValue(String key) {
        if (!hasCompleteLocalLlmTextConfig()) {
            return null;
        }
        if (key != null && key.startsWith("ai.agent-bindings.") && key.endsWith(".model")) {
            return "ai.service.text";
        }
        String envName = switch (key == null ? "" : key) {
            case "ai.service.text.provider" -> "LLM_PROVIDER";
            case "ai.service.text.base-url" -> "LLM_BASE_URL";
            case "ai.service.text.api-key" -> "LLM_API_KEY";
            case "ai.service.text.model" -> "LLM_MODEL";
            default -> "";
        };
        return envName.isEmpty() ? null : trimToNull(environment.getProperty(envName));
    }

    private boolean hasCompleteLocalLlmTextConfig() {
        return trimToNull(environment.getProperty("LLM_PROVIDER")) != null
                && trimToNull(environment.getProperty("LLM_BASE_URL")) != null
                && trimToNull(environment.getProperty("LLM_API_KEY")) != null
                && trimToNull(environment.getProperty("LLM_MODEL")) != null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Override
    public Long getLongValue(String key, Long defaultValue) {
        String value = getValue(key, null);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    @Override
    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        String value = getValue(key, null);
        if (value == null || value.isBlank()) return defaultValue;
        return Boolean.parseBoolean(value);
    }
}
