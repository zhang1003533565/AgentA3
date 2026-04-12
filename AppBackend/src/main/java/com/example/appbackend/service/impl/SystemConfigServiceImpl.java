package com.example.appbackend.service.impl;

import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.service.SystemConfigService;
import org.springframework.stereotype.Service;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigServiceImpl(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public String getValue(String key, String defaultValue) {
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
