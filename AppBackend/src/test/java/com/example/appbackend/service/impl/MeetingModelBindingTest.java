package com.example.appbackend.service.impl;

import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.repository.SystemConfigRepository;
import com.example.appbackend.repository.SystemConfigTestLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第九步：Agent 2（会后纪要）模型绑定优先级与默认模型选择确定性。
 *
 * <p>Agent 1（会中实时总结）与 Agent 2 共用 agentName=meeting_summary_agent 和通用绑定键
 * {@code ai.agent-bindings.meeting_summary_agent.model}，因此本测试同时锁定：
 * 会后纪要只读专属键 {@code ...minutes-model}，不得影响 Agent 1 的模型来源。
 */
class MeetingModelBindingTest {

    private static final String MINUTES_KEY = MeetingServiceImpl.AI_MINUTES_MODEL_BINDING_KEY;
    private static final String LEGACY_AGENT_KEY = "ai.agent-bindings.meeting_summary_agent.model";

    /** 完整文本模型配置：provider/base-url/api-key/model 四项齐备。 */
    private static Map<String, String> textConfig(String prefix, String provider, String model) {
        Map<String, String> fields = new HashMap<>();
        fields.put(prefix + ".provider", provider);
        fields.put(prefix + ".base-url", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        fields.put(prefix + ".api-key", "sk-test-key");
        fields.put(prefix + ".model", model);
        return fields;
    }

    private static MeetingServiceImpl serviceWith(Map<String, String> configs, List<String> testedKeys) {
        SystemConfigRepository repository = mock(SystemConfigRepository.class);
        SystemConfigTestLogRepository testLogRepository = mock(SystemConfigTestLogRepository.class);

        when(repository.findByConfigKeyAndStatus(any(String.class), eq(1))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = configs.get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setStatus(1);
            return Optional.of(config);
        });
        when(repository.findByConfigKeyStartingWithAndStatus(any(String.class), eq(1))).thenAnswer(invocation -> {
            String prefix = invocation.getArgument(0);
            List<SystemConfig> matched = new ArrayList<>();
            configs.forEach((key, value) -> {
                if (key.startsWith(prefix) && value != null && !value.isBlank()) {
                    SystemConfig config = new SystemConfig();
                    config.setConfigKey(key);
                    config.setConfigValue(value);
                    config.setStatus(1);
                    matched.add(config);
                }
            });
            return matched;
        });
        when(testLogRepository.findByConfigKeyStartingWithAndSuccessOrderByCreateTimeDescIdDesc(
                any(String.class), eq(true), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    String prefix = invocation.getArgument(0);
                    List<com.example.appbackend.entity.SystemConfigTestLog> logs = new ArrayList<>();
                    for (String key : testedKeys) {
                        if (key.startsWith(prefix)) {
                            com.example.appbackend.entity.SystemConfigTestLog log =
                                    new com.example.appbackend.entity.SystemConfigTestLog();
                            log.setConfigKey(key);
                            logs.add(log);
                        }
                    }
                    return logs;
                });

        return new MeetingServiceImpl(null, null, null, null, null, null,
                repository, testLogRepository, null, null, null, null);
    }

    // ---------- 测试 1：Agent 2 有专属绑定时优先使用 ----------
    @Test
    void minutesFlowPrefersItsOwnBinding() {
        Map<String, String> configs = new HashMap<>();
        configs.put(MINUTES_KEY, "ai.service.text.qwen3-8-max");
        configs.putAll(textConfig("ai.service.text.qwen3-8-max", "qwen", "qwen3.8-max"));
        // 通用默认配置（历史行为会选它），用于证明专属绑定确实优先生效
        configs.putAll(textConfig("ai.service.text.qwen3-7-max", "qwen", "qwen3.7-max"));
        List<String> tested = List.of("ai.service.text.qwen3-7-max.model");

        String resolved = serviceWith(configs, tested).resolveMeetingLlmModel(null, MINUTES_KEY);

        assertEquals("ai.service.text.qwen3-8-max", resolved);
    }

    // ---------- 测试 2：多套完整配置时默认选择必须确定 ----------
    @Test
    void defaultModelSelectionIsDeterministic() {
        Map<String, String> configs = new HashMap<>();
        // 故意按"反优先级"顺序写入，若实现依赖 HashMap 遍历顺序就会漂到最后一个
        configs.putAll(textConfig("ai.service.text.zzz-kimi", "qwen", "kimi-k3"));
        configs.putAll(textConfig("ai.service.text.middle-glm", "qwen", "glm-5.2"));
        configs.putAll(textConfig("ai.service.text.aaa-deepseek", "qwen", "deepseek-v4-flash-0731"));
        MeetingServiceImpl service = serviceWith(configs, List.of());

        String first = service.firstCompleteTextModelPrefix();
        for (int i = 0; i < 200; i++) {
            assertEquals(first, serviceWith(configs, List.of()).firstCompleteTextModelPrefix(),
                    "默认模型随调用发生变化，说明仍存在不确定选择");
        }
        // 结果必须是项目既有优先级里的首选，而不是哈希顺序的偶然结果
        assertEquals("ai.service.text.aaa-deepseek", first);
    }

    // ---------- 测试 3：专属绑定可用时不走默认、不误入兜底 ----------
    @Test
    void usableBindingSkipsDefaultChain() {
        Map<String, String> configs = new HashMap<>();
        configs.put(MINUTES_KEY, "ai.service.text.qwen3-8-max");
        configs.putAll(textConfig("ai.service.text.qwen3-8-max", "qwen", "qwen3.8-max"));
        configs.putAll(textConfig("ai.service.text.qwen3-7-max", "qwen", "qwen3.7-max"));
        MeetingServiceImpl service = serviceWith(configs, List.of("ai.service.text.qwen3-7-max.model"));

        String resolved = service.resolveMeetingLlmModel(null, MINUTES_KEY);

        assertEquals("ai.service.text.qwen3-8-max", resolved);
        assertFalse(resolved.contains("qwen3-7-max"), "专属绑定可用时不应落到默认模型");
    }

    // ---------- 测试 4：专属绑定不可用时退回原有机制 ----------
    @Test
    void unusableBindingFallsBackWithoutFailing() {
        // 4a 绑定指向不存在的配置
        Map<String, String> missing = new HashMap<>();
        missing.put(MINUTES_KEY, "ai.service.text.not-configured");
        missing.putAll(textConfig("ai.service.text.qwen3-7-max", "qwen", "qwen3.7-max"));
        MeetingServiceImpl missingService = serviceWith(missing, List.of("ai.service.text.qwen3-7-max.model"));
        assertEquals("ai.service.text.qwen3-7-max",
                missingService.resolveMeetingLlmModel(null, MINUTES_KEY));

        // 4b 绑定指向配置不完整（缺 api-key）
        Map<String, String> incomplete = new HashMap<>();
        incomplete.put(MINUTES_KEY, "ai.service.text.broken");
        incomplete.put("ai.service.text.broken.provider", "qwen");
        incomplete.put("ai.service.text.broken.model", "qwen3.8-max");
        incomplete.putAll(textConfig("ai.service.text.qwen3-7-max", "qwen", "qwen3.7-max"));
        MeetingServiceImpl incompleteService = serviceWith(incomplete, List.of("ai.service.text.qwen3-7-max.model"));
        assertEquals("ai.service.text.qwen3-7-max",
                incompleteService.resolveMeetingLlmModel(null, MINUTES_KEY));

        // 4c 完全没有绑定键：保持既有行为（走最近测试成功的模型）
        MeetingServiceImpl noKeyService = serviceWith(missing, List.of("ai.service.text.qwen3-7-max.model"));
        assertEquals("ai.service.text.qwen3-7-max", noKeyService.resolveMeetingLlmModel(null, null));

        // 4d 绑定键存在但值指向免费清单外且会被改写的配置：视为不可用，继续兜底
        Map<String, String> paidOnly = new HashMap<>();
        paidOnly.put(MINUTES_KEY, "ai.service.text.qwen3-7-max");
        paidOnly.putAll(textConfig("ai.service.text.qwen3-7-max", "qwen", "qwen3.7-max"));
        paidOnly.putAll(textConfig("ai.service.text.qwen3-8-max", "qwen", "qwen3.8-max"));
        MeetingServiceImpl paidService = serviceWith(paidOnly, List.of("ai.service.text.qwen3-8-max.model"));
        // qwen3.7-max 不在免费清单 => 该绑定被判不可用 => 落到最近测试成功的 qwen3-8-max
        assertEquals("ai.service.text.qwen3-8-max", paidService.resolveMeetingLlmModel(null, MINUTES_KEY));
    }

    // ---------- 测试 5：Agent 1 的模型来源不受本次修改影响 ----------
    @Test
    void agentOneBindingRemainsUntouched() {
        Map<String, String> configs = new HashMap<>();
        configs.put(MINUTES_KEY, "ai.service.text.qwen3-8-max");
        configs.putAll(textConfig("ai.service.text.qwen3-8-max", "qwen", "qwen3.8-max"));
        configs.put(LEGACY_AGENT_KEY, "ai.service.text.qwen3-7-max");
        configs.putAll(textConfig("ai.service.text.qwen3-7-max", "qwen", "qwen3.7-max"));
        MeetingServiceImpl service = serviceWith(configs, List.of("ai.service.text.qwen3-7-max.model"));

        // Agent 1 走的是通用聊天链路（读 .model 键），不经过 resolveMeetingLlmModel；
        // 这里锁定：不传专属键时，会后/其他会议流程的既有选择顺序完全不变。
        assertEquals("ai.service.text.qwen3-7-max", service.resolveMeetingLlmModel(null, null));
        // 单参重载（runAgent / previewAgent / organizeMeetingResults 仍在用）行为不变
        assertEquals("ai.service.text.qwen3-7-max", service.resolveMeetingLlmModel(null));
        // 专属键与 Agent 1 使用的通用键必须是不同配置，否则升级 Agent 2 会连带改 Agent 1
        assertFalse(MINUTES_KEY.equals(LEGACY_AGENT_KEY));
        assertTrue(MINUTES_KEY.endsWith("minutes-model"), "专属键命名需与通用 .model 明确区分");
    }

    @Test
    void explicitModelStillWinsOverBinding() {
        Map<String, String> configs = new HashMap<>();
        configs.put(MINUTES_KEY, "ai.service.text.qwen3-8-max");
        configs.putAll(textConfig("ai.service.text.qwen3-8-max", "qwen", "qwen3.8-max"));
        MeetingServiceImpl service = serviceWith(configs, List.of());

        assertEquals("ai.service.text.manual", service.resolveMeetingLlmModel("ai.service.text.manual", MINUTES_KEY));
    }
}
