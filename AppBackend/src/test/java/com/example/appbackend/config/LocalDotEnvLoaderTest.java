package com.example.appbackend.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDotEnvLoaderTest {

    private static final List<String> KEYS = List.of(
            "LLM_PROVIDER",
            "LLM_BASE_URL",
            "LLM_API_KEY",
            "LLM_MODEL"
    );

    @AfterEach
    void clearProperties() {
        KEYS.forEach(System::clearProperty);
    }

    @Test
    void loadsRootDotEnvBeforeBackendDotEnv(@TempDir Path root) throws Exception {
        Path backend = Files.createDirectories(root.resolve("AppBackend"));
        Files.writeString(root.resolve(".env"), """
                LLM_PROVIDER=deepseek
                LLM_BASE_URL=https://api.deepseek.com
                LLM_API_KEY=local-key
                LLM_MODEL=deepseek-chat
                """);
        Files.writeString(backend.resolve(".env"), "LLM_MODEL=backend-model\n");

        List<String> loaded = LocalDotEnvLoader.loadForWorkingDirectory(backend);

        assertEquals("deepseek", System.getProperty("LLM_PROVIDER"));
        assertEquals("https://api.deepseek.com", System.getProperty("LLM_BASE_URL"));
        assertEquals("local-key", System.getProperty("LLM_API_KEY"));
        assertEquals("deepseek-chat", System.getProperty("LLM_MODEL"));
        assertTrue(loaded.containsAll(KEYS));
    }

    @Test
    void keepsExistingSystemProperty(@TempDir Path root) throws Exception {
        System.setProperty("LLM_API_KEY", "already-set");
        Files.writeString(root.resolve(".env"), "LLM_API_KEY=from-dotenv\n");

        LocalDotEnvLoader.loadForWorkingDirectory(root);

        assertEquals("already-set", System.getProperty("LLM_API_KEY"));
    }
}
