package com.example.appbackend.util;

import com.example.appbackend.service.SystemConfigService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    @ParameterizedTest
    @NullAndEmptySource
    void generateTokenFailsClosedWhenJwtSecretIsMissing(String secret) {
        JwtUtil jwtUtil = new JwtUtil(new StubSystemConfigService(secret));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> jwtUtil.generateToken("demo", 1L, "student"));

        assertEquals("JWT secret is not configured", error.getMessage());
    }

    private static final class StubSystemConfigService implements SystemConfigService {
        private final String jwtSecret;

        private StubSystemConfigService(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        @Override
        public String getValue(String key, String defaultValue) {
            if (!"jwt.secret".equals(key)) return defaultValue;
            return jwtSecret == null || jwtSecret.isBlank() ? defaultValue : jwtSecret;
        }

        @Override
        public Long getLongValue(String key, Long defaultValue) {
            return defaultValue;
        }

        @Override
        public Boolean getBooleanValue(String key, Boolean defaultValue) {
            return defaultValue;
        }
    }
}
