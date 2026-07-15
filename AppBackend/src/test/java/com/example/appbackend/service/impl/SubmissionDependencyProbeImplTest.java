package com.example.appbackend.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionDependencyProbeImplTest {

    @Test
    void reportsDatabaseRedisAndAuthenticatedAiReadiness() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                return requiredType.cast(1);
            }
        };
        SubmissionDependencyProbeImpl probe = new SubmissionDependencyProbeImpl(
                jdbcTemplate,
                "redis",
                6379,
                "http://ai-server:8081",
                "shared-internal-token",
                1000) {
            @Override
            protected boolean redisReady() {
                return true;
            }

            @Override
            protected boolean aiServerReady() {
                return true;
            }
        };

        assertEquals(
                Map.of("database", "UP", "redis", "UP", "aiServer", "UP"),
                probe.probe());
    }

    @Test
    void reportsOnlyFailedDependencyWithoutLeakingExceptionDetails() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                throw new IllegalStateException("jdbc:mysql://user:secret@mysql/private");
            }
        };
        SubmissionDependencyProbeImpl probe = new SubmissionDependencyProbeImpl(
                jdbcTemplate,
                "redis",
                6379,
                "http://ai-server:8081",
                "shared-internal-token",
                1000) {
            @Override
            protected boolean redisReady() {
                return true;
            }

            @Override
            protected boolean aiServerReady() {
                return true;
            }
        };

        assertEquals(
                Map.of("database", "DOWN", "redis", "UP", "aiServer", "UP"),
                probe.probe());
    }
}
