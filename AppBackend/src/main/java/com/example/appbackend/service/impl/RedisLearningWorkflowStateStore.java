package com.example.appbackend.service.impl;

import com.example.appbackend.service.LearningWorkflowStateStore;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisLearningWorkflowStateStore implements LearningWorkflowStateStore {
    private static final Logger log = LoggerFactory.getLogger(RedisLearningWorkflowStateStore.class);
    private static final String KEY_PREFIX = "learning:workflow:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisClient redisClient;
    private final ObjectMapper canonicalMapper;
    private final ConcurrentHashMap<String, CacheEntry> fallback = new ConcurrentHashMap<>();

    @Autowired
    public RedisLearningWorkflowStateStore(
            ObjectMapper objectMapper,
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.connect-timeout:2s}") Duration connectTimeout,
            @Value("${spring.data.redis.timeout:2s}") Duration commandTimeout) {
        this(new RespRedisClient(host, port, password, database,
                connectTimeout, commandTimeout), objectMapper);
    }

    RedisLearningWorkflowStateStore(RedisClient redisClient, ObjectMapper objectMapper) {
        this.redisClient = redisClient;
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public void save(WorkflowState state) {
        if (state == null || !StringUtils.hasText(state.getWorkflowId())) {
            throw new IllegalArgumentException("学习工作流状态无效");
        }
        String workflowId = state.getWorkflowId().trim();
        Instant expiresAt = Instant.now().plus(TTL);
        fallback.put(workflowId, new CacheEntry(state, expiresAt));
        try {
            redisClient.set(key(workflowId), canonicalMapper.writeValueAsString(state), TTL);
        } catch (Exception error) {
            log.warn("learning workflow redis save unavailable workflowId={} errorType={}",
                    workflowId, error.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<WorkflowState> find(String workflowId) {
        if (!StringUtils.hasText(workflowId)) {
            return Optional.empty();
        }
        String normalized = workflowId.trim();
        try {
            String json = redisClient.get(key(normalized));
            if (StringUtils.hasText(json)) {
                WorkflowState restored = canonicalMapper.readValue(json, WorkflowState.class);
                fallback.put(normalized, new CacheEntry(restored, Instant.now().plus(TTL)));
                return Optional.of(restored);
            }
        } catch (Exception error) {
            log.warn("learning workflow redis read unavailable workflowId={} errorType={}",
                    normalized, error.getClass().getSimpleName());
        }
        CacheEntry cached = fallback.get(normalized);
        if (cached == null) {
            return Optional.empty();
        }
        if (!cached.expiresAt().isAfter(Instant.now())) {
            fallback.remove(normalized, cached);
            return Optional.empty();
        }
        return Optional.of(cached.state());
    }

    private String key(String workflowId) {
        return KEY_PREFIX + workflowId;
    }

    private record CacheEntry(WorkflowState state, Instant expiresAt) {
    }

    interface RedisClient {
        void set(String key, String value, Duration ttl) throws IOException;

        String get(String key) throws IOException;
    }

    /**
     * Minimal RESP2 client for GET and SET EX. A short-lived socket keeps this adapter
     * dependency-free and isolated from the application's Spring Boot/Spring Data versions.
     */
    static final class RespRedisClient implements RedisClient {
        private static final int MAX_BULK_RESPONSE_BYTES = 4 * 1024 * 1024;
        private static final int MAX_RESPONSE_LINE_BYTES = 8 * 1024;
        private static final byte[] CRLF = new byte[]{'\r', '\n'};

        private final String host;
        private final int port;
        private final String password;
        private final int database;
        private final int connectTimeoutMillis;
        private final int commandTimeoutMillis;

        RespRedisClient(String host,
                        int port,
                        String password,
                        int database,
                        Duration connectTimeout,
                        Duration commandTimeout) {
            if (!StringUtils.hasText(host) || port < 1 || port > 65_535 || database < 0) {
                throw new IllegalArgumentException("Redis 连接配置无效");
            }
            this.host = host.trim();
            this.port = port;
            this.password = password == null ? "" : password;
            this.database = database;
            this.connectTimeoutMillis = timeoutMillis(connectTimeout);
            this.commandTimeoutMillis = timeoutMillis(commandTimeout);
        }

        @Override
        public void set(String key, String value, Duration ttl) throws IOException {
            if (!StringUtils.hasText(key) || value == null || ttl == null
                    || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("Redis 写入参数无效");
            }
            Object response = execute(List.of(
                    "SET", key, value, "EX", Long.toString(ttl.toSeconds())));
            if (!"OK".equals(response)) {
                throw new IOException("Redis SET 未确认");
            }
        }

        @Override
        public String get(String key) throws IOException {
            if (!StringUtils.hasText(key)) {
                return null;
            }
            Object response = execute(List.of("GET", key));
            if (response == null || response instanceof String) {
                return (String) response;
            }
            throw new IOException("Redis GET 响应类型无效");
        }

        private Object execute(List<String> command) throws IOException {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
                socket.setSoTimeout(commandTimeoutMillis);
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();
                if (!password.isEmpty()) {
                    requireOk(run(output, input, List.of("AUTH", password)), "AUTH");
                }
                if (database != 0) {
                    requireOk(run(output, input,
                            List.of("SELECT", Integer.toString(database))), "SELECT");
                }
                return run(output, input, command);
            }
        }

        private Object run(OutputStream output,
                           InputStream input,
                           List<String> command) throws IOException {
            output.write(encodeCommand(command));
            output.flush();
            return readResponse(input);
        }

        private void requireOk(Object response, String command) throws IOException {
            if (!"OK".equals(response)) {
                throw new IOException("Redis " + command + " 未确认");
            }
        }

        static byte[] encodeCommand(List<String> command) {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Redis 命令不能为空");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            writeAscii(output, "*" + command.size());
            output.writeBytes(CRLF);
            for (String argument : command) {
                byte[] bytes = String.valueOf(argument).getBytes(StandardCharsets.UTF_8);
                writeAscii(output, "$" + bytes.length);
                output.writeBytes(CRLF);
                output.writeBytes(bytes);
                output.writeBytes(CRLF);
            }
            return output.toByteArray();
        }

        static Object readResponse(InputStream input) throws IOException {
            int prefix = input.read();
            if (prefix < 0) {
                throw new IOException("Redis 响应意外结束");
            }
            return switch (prefix) {
                case '+' -> readLine(input);
                case '-' -> {
                    readLine(input);
                    throw new IOException("Redis 命令执行失败");
                }
                case ':' -> parseLong(readLine(input), "Redis 整数响应无效");
                case '$' -> readBulkString(input);
                case '*' -> readArray(input);
                default -> throw new IOException("Redis 响应协议无效");
            };
        }

        private static String readBulkString(InputStream input) throws IOException {
            long length = parseLong(readLine(input), "Redis 批量响应长度无效");
            if (length == -1) {
                return null;
            }
            if (length < 0 || length > MAX_BULK_RESPONSE_BYTES) {
                throw new IOException("Redis 批量响应超过限制");
            }
            byte[] bytes = input.readNBytes((int) length);
            if (bytes.length != length) {
                throw new IOException("Redis 批量响应不完整");
            }
            requireCrlf(input);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private static List<Object> readArray(InputStream input) throws IOException {
            long size = parseLong(readLine(input), "Redis 数组响应长度无效");
            if (size == -1) {
                return null;
            }
            if (size < 0 || size > 64) {
                throw new IOException("Redis 数组响应超过限制");
            }
            List<Object> values = new ArrayList<>((int) size);
            for (int index = 0; index < size; index++) {
                values.add(readResponse(input));
            }
            return values;
        }

        private static String readLine(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (line.size() <= MAX_RESPONSE_LINE_BYTES) {
                int current = input.read();
                if (current < 0) {
                    throw new IOException("Redis 响应行意外结束");
                }
                if (current == '\r') {
                    if (input.read() != '\n') {
                        throw new IOException("Redis 响应行结尾无效");
                    }
                    return line.toString(StandardCharsets.UTF_8);
                }
                line.write(current);
            }
            throw new IOException("Redis 响应行超过限制");
        }

        private static void requireCrlf(InputStream input) throws IOException {
            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("Redis 批量响应结尾无效");
            }
        }

        private static long parseLong(String value, String message) throws IOException {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException error) {
                throw new IOException(message, error);
            }
        }

        private static void writeAscii(ByteArrayOutputStream output, String value) {
            output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
        }

        private static int timeoutMillis(Duration timeout) {
            Duration safe = timeout == null || timeout.isZero() || timeout.isNegative()
                    ? Duration.ofSeconds(2) : timeout;
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, safe.toMillis()));
        }
    }
}
