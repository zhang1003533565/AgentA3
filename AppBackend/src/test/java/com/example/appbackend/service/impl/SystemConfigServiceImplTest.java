package com.example.appbackend.service.impl;

import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemConfigServiceImplTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jwt.secret",
            "tencent.map.key",
            "tencent.cos.secret-id",
            "tencent.cos.secret-key",
            "amap.app.key",
            "amap.web.key",
            "aliyun.oss.access-key-id",
            "aliyun.oss.access-key-secret"
    })
    void sensitiveKeysComeOnlyFromSpringEnvironment(String key) {
        RecordingRepository repository = new RecordingRepository(config(key, "database-value"));
        MockEnvironment environment = new MockEnvironment().withProperty(key, "environment-value");
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), environment);

        assertEquals("environment-value", service.getValue(key, ""));
        assertEquals(0, repository.findActiveCalls());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jwt.secret",
            "tencent.map.key",
            "tencent.cos.secret-id",
            "tencent.cos.secret-key",
            "amap.app.key",
            "amap.web.key",
            "aliyun.oss.access-key-id",
            "aliyun.oss.access-key-secret"
    })
    void sensitiveKeysNeverFallBackToDatabase(String key) {
        RecordingRepository repository = new RecordingRepository(config(key, "database-value"));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), new MockEnvironment());

        assertEquals("", service.getValue(key, ""));
        assertEquals(0, repository.findActiveCalls());
    }

    @Test
    void nonSensitiveKeysRemainDatabaseBacked() {
        String key = "tencent.map.base-url";
        RecordingRepository repository = new RecordingRepository(
                config(key, "https://database.example.invalid"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty(key, "https://environment.example.invalid");
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), environment);

        assertEquals("https://database.example.invalid", service.getValue(key, "fallback"));
        assertEquals(1, repository.findActiveCalls());
    }

    @Test
    void completeLocalLlmConfigWithOverrideFlagOverridesDatabaseTextConfig() {
        String key = "ai.service.text.api-key";
        RecordingRepository repository = new RecordingRepository(config(key, "database-key"));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), completeLocalLlmEnvironment());

        assertEquals("local-key", service.getValue(key, ""));
        assertEquals(0, repository.findActiveCalls());
    }

    @Test
    void completeLocalLlmConfigWithOverrideFlagOverridesAgentBindingToDefaultTextPrefix() {
        String key = "ai.agent-bindings.diagram_architecture_agent.model";
        RecordingRepository repository = new RecordingRepository(config(key, "ai.service.text.public"));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), completeLocalLlmEnvironment());

        assertEquals("ai.service.text", service.getValue(key, ""));
        assertEquals(0, repository.findActiveCalls());
    }

    @Test
    void completeLocalLlmConfigWithoutOverrideFlagFallsBackToDatabase() {
        String key = "ai.service.text.api-key";
        RecordingRepository repository = new RecordingRepository(config(key, "database-key"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LLM_PROVIDER", "deepseek")
                .withProperty("LLM_BASE_URL", "https://api.deepseek.com")
                .withProperty("LLM_API_KEY", "local-key")
                .withProperty("LLM_MODEL", "deepseek-chat");
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), environment);

        assertEquals("database-key", service.getValue(key, ""));
        assertEquals(1, repository.findActiveCalls());
    }

    @Test
    void partialLocalLlmConfigFallsBackToDatabase() {
        String key = "ai.service.text.api-key";
        RecordingRepository repository = new RecordingRepository(config(key, "database-key"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("LLM_API_KEY", "local-key")
                .withProperty("LLM_CONFIG_OVERRIDE", "true");
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository.proxy(), environment);

        assertEquals("database-key", service.getValue(key, ""));
        assertEquals(1, repository.findActiveCalls());
    }

    private static MockEnvironment completeLocalLlmEnvironment() {
        return new MockEnvironment()
                .withProperty("LLM_PROVIDER", "deepseek")
                .withProperty("LLM_BASE_URL", "https://api.deepseek.com")
                .withProperty("LLM_API_KEY", "local-key")
                .withProperty("LLM_MODEL", "deepseek-chat")
                .withProperty("LLM_CONFIG_OVERRIDE", "true");
    }

    private static SystemConfig config(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setStatus(1);
        return config;
    }

    private static final class RecordingRepository implements InvocationHandler {
        private final SystemConfig stored;
        private int findActiveCalls;

        private RecordingRepository(SystemConfig stored) {
            this.stored = stored;
        }

        private SystemConfigRepository proxy() {
            return (SystemConfigRepository) Proxy.newProxyInstance(
                    SystemConfigRepository.class.getClassLoader(),
                    new Class<?>[]{SystemConfigRepository.class},
                    this
            );
        }

        private int findActiveCalls() {
            return findActiveCalls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("findByConfigKeyAndStatus".equals(method.getName())) {
                findActiveCalls++;
                return Optional.ofNullable(stored);
            }
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "RecordingRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            throw new UnsupportedOperationException(method.getName());
        }
    }
}
