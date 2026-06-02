package com.example.appbackend.config;

import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.repository.SystemConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class XfyunAsrConfigInitializer implements ApplicationRunner {

    private static final String CONFIG_GROUP = "asr";
    private static final List<ConfigDefault> CONFIG_DEFAULTS = List.of(
            new ConfigDefault("ai.asr.xfyun.websocket-url", "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1", "讯飞实时转写大模型 WebSocket 地址"),
            new ConfigDefault("ai.asr.xfyun.app-id", "", "讯飞实时转写大模型 App ID"),
            new ConfigDefault("ai.asr.xfyun.access-key-id", "", "讯飞实时转写大模型 AccessKeyId/APIKey"),
            new ConfigDefault("ai.asr.xfyun.access-key-secret", "", "讯飞实时转写大模型 AccessKeySecret/APISecret"),
            new ConfigDefault("ai.asr.xfyun.lang", "autodialect", "讯飞实时转写大模型语种"),
            new ConfigDefault("ai.asr.xfyun.audio-encode", "pcm_s16le", "讯飞实时转写大模型音频编码"),
            new ConfigDefault("ai.asr.xfyun.samplerate", "16000", "讯飞实时转写大模型采样率")
    );

    private final SystemConfigRepository systemConfigRepository;

    public XfyunAsrConfigInitializer(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        CONFIG_DEFAULTS.forEach(this::ensureConfig);
    }

    private void ensureConfig(ConfigDefault configDefault) {
        SystemConfig config = systemConfigRepository.findByConfigKey(configDefault.key())
                .orElseGet(SystemConfig::new);
        boolean isNew = config.getId() == null;
        boolean groupChanged = !CONFIG_GROUP.equals(config.getConfigGroup());
        if (!isNew && !groupChanged) {
            return;
        }
        if (isNew) {
            config.setConfigKey(configDefault.key());
            config.setConfigValue(configDefault.value());
            config.setDescription(configDefault.description());
            config.setStatus(1);
            config.setIsDefault(0);
        }
        config.setConfigGroup(CONFIG_GROUP);
        systemConfigRepository.save(config);
    }

    private record ConfigDefault(String key, String value, String description) {
    }
}
