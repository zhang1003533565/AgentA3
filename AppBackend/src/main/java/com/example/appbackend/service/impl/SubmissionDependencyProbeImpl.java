package com.example.appbackend.service.impl;

import com.example.appbackend.service.SubmissionDependencyProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SubmissionDependencyProbeImpl implements SubmissionDependencyProbe {
    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String INTERNAL_TOKEN_HEADER = "X-AI-Internal-Token";

    private final JdbcTemplate jdbcTemplate;
    private final String redisHost;
    private final int redisPort;
    private final String aiReadinessUrl;
    private final String aiInternalToken;
    private final int timeoutMillis;
    private final HttpClient httpClient;

    public SubmissionDependencyProbeImpl(
            JdbcTemplate jdbcTemplate,
            @Value("${REDIS_HOST:localhost}") String redisHost,
            @Value("${REDIS_PORT:6379}") int redisPort,
            @Value("${ai.python.base-url:http://localhost:8081}") String aiBaseUrl,
            @Value("${ai.python.internal-token:}") String aiInternalToken,
            @Value("${SUBMISSION_READINESS_TIMEOUT_MS:3000}") int timeoutMillis) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisHost = redisHost == null || redisHost.isBlank() ? "localhost" : redisHost.trim();
        this.redisPort = redisPort;
        this.aiReadinessUrl = trimTrailingSlash(aiBaseUrl) + "/internal/readiness";
        this.aiInternalToken = aiInternalToken == null ? "" : aiInternalToken.trim();
        this.timeoutMillis = Math.max(250, timeoutMillis);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMillis))
                .build();
    }

    @Override
    public Map<String, String> probe() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", probeSafely(this::databaseReady));
        components.put("redis", probeSafely(this::redisReady));
        components.put("aiServer", probeSafely(this::aiServerReady));
        return components;
    }

    protected boolean databaseReady() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return result != null && result == 1;
    }

    protected boolean redisReady() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            socket.getOutputStream().write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                return "+PONG".equals(reader.readLine());
            }
        }
    }

    protected boolean aiServerReady() throws Exception {
        if (aiInternalToken.isBlank()) {
            return false;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(aiReadinessUrl))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header(INTERNAL_TOKEN_HEADER, aiInternalToken)
                .GET()
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private String probeSafely(CheckedProbe probe) {
        try {
            return probe.ready() ? UP : DOWN;
        } catch (Exception ignored) {
            return DOWN;
        }
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:8081" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @FunctionalInterface
    private interface CheckedProbe {
        boolean ready() throws Exception;
    }
}
