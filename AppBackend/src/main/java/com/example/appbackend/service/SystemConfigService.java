package com.example.appbackend.service;

public interface SystemConfigService {

    String getValue(String key, String defaultValue);

    Long getLongValue(String key, Long defaultValue);

    Boolean getBooleanValue(String key, Boolean defaultValue);
}
