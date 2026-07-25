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
        if (ENVIRONMENT_ONLY_KEYS.contains(key)) {
            String value = environment.getProperty(key);
            return value == null || value.isBlank() ? defaultValue : value;
        }
        return systemConfigRepository.findByConfigKeyAndStatus(key, 1)
                .map(item -> item.getConfigValue())
                .filter(value -> value != null && !value.isBlank())
                .orElse(defaultValue);
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
