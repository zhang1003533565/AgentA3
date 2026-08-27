package com.example.appbackend.service.impl;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一约束默认文本模型，避免旧的已测试配置按字典序被误选为默认模型。
 * 这里只约束文本生成链路；视觉、图片、视频、语音和向量模型不走这套清单。
 */
final class AiModelPolicy {

    private static final List<String> FREE_TEXT_MODEL_PRIORITY = List.of(
            "deepseek-v4-flash-0731",
            "glm-5.2",
            "kimi-k2.7-code",
            "deepseek-v4-pro-0813",
            "qwen3.5-ocr",
            "qwen3.7-plus-2026-05-26",
            "qwen3.8-2.4t-a95b",
            "qwen3.8-max",
            // Kimi K3 的 max 推理延迟最高，只作为这组百炼免费模型的最后兜底。
            "kimi-k3",
            // 保留历史模型名，避免已有绑定失效；它们不参与当前默认免费链首选。
            "qwen3.7-max-2026-06-08",
            "qwen3.7-max-2026-05-17",
            "qwen3.7-max-preview",
            // OpenCode Go and the official DeepSeek endpoint both expose this Flash model.
            "deepseek-v4-flash-vision-exp",
            "deepseek-v4-flash"
    );

    private static final Set<String> QWEN_PROVIDER_ALIASES = Set.of(
            "qwen", "dashscope", "aliyun", "aliyun_qwen", "aliyun-qwen",
            "alibaba_qwen", "alibaba-qwen", "qwen_openai", "qwen-openai"
    );

    private AiModelPolicy() {
    }

    static boolean isFreeTextModel(String model) {
        return FREE_TEXT_MODEL_PRIORITY.contains(normalize(model));
    }

    /**
     * 百炼同一套 OpenAI 兼容配置可以直接切换到用户列出的免费模型。
     * 这样旧的 qwen-plus/qwen-max 配置无需继续调用付费模型，也不会要求用户重填 Key。
     */
    static String effectiveFreeTextModel(String provider, String configuredModel) {
        String normalizedModel = normalize(configuredModel);
        if (isFreeTextModel(normalizedModel)) {
            return configuredModel.trim();
        }
        return QWEN_PROVIDER_ALIASES.contains(normalize(provider)) ? defaultTextModel() : "";
    }

    static int priority(String model) {
        int index = FREE_TEXT_MODEL_PRIORITY.indexOf(normalize(model));
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    static String defaultTextModel() {
        return FREE_TEXT_MODEL_PRIORITY.get(0);
    }

    static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private static String normalize(String model) {
        return model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
    }
}
