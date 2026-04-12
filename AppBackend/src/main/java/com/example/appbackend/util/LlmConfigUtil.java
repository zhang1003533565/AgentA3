package com.example.appbackend.util;

import com.example.appbackend.config.DeepSeekConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LlmConfigUtil {

    private final DeepSeekConfig deepSeekConfig;

    public LlmConfigUtil(DeepSeekConfig deepSeekConfig) {
        this.deepSeekConfig = deepSeekConfig;
    }

    public String getBaseUrl() {
        return deepSeekConfig.getBaseUrl();
    }

    public String getApiKey() {
        return deepSeekConfig.getApiKey();
    }

    public String getModel() {
        return deepSeekConfig.getModel();
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(deepSeekConfig.getApiKey());
    }
}
