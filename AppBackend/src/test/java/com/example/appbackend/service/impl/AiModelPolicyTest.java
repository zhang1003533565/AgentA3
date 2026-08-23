package com.example.appbackend.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelPolicyTest {

    @Test
    void acceptsDeepSeekFlashForOpenCodeProvider() {
        assertTrue(AiModelPolicy.isFreeTextModel("deepseek-v4-flash"));
        assertEquals(
                "deepseek-v4-flash",
                AiModelPolicy.effectiveFreeTextModel("opencode", "deepseek-v4-flash")
        );
    }

    @Test
    void acceptsDeepSeekFlashVisionForOfficialProvider() {
        assertTrue(AiModelPolicy.isFreeTextModel("deepseek-v4-flash-vision-exp"));
        assertEquals(
                "deepseek-v4-flash-vision-exp",
                AiModelPolicy.effectiveFreeTextModel("deepseek", "deepseek-v4-flash-vision-exp")
        );
    }

    @Test
    void keepsExistingQwenDefaultPriority() {
        assertEquals("qwen3.7-max-2026-06-08", AiModelPolicy.defaultTextModel());
    }
}
