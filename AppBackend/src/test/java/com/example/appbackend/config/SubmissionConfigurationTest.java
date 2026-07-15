package com.example.appbackend.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class SubmissionConfigurationTest {
    @Test
    void applicationYamlContainsNoLiteralSecrets() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertFalse(yaml.matches("(?s).*secret-id:\\s*+(?!\\$\\{).+"));
        assertFalse(yaml.matches("(?s).*secret-key:\\s*+(?!\\$\\{).+"));
        assertFalse(yaml.matches("(?s).*access-key-secret:\\s*+(?!\\$\\{).+"));
        assertFalse(yaml.matches("(?s).*jwt:\\s*\\n\\s+secret:\\s*+(?!\\$\\{).+"));
    }
}
