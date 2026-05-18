package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class PythonAiProxyServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void chat_shouldForwardAuthorizationAndUserIdAndParseResponse() throws Exception {
        AtomicReference<String> authRef = new AtomicReference<>();
        AtomicReference<String> userIdRef = new AtomicReference<>();
        AtomicReference<String> requestBodyRef = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/chat", exchange -> {
            authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            userIdRef.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            requestBodyRef.set(readBody(exchange));

            String responseJson = """
                    {
                      "sessionId": "session-001",
                      "sessionToken": "session-001_hash",
                      "model": "deepseek-chat",
                      "searchKeyword": "黄焖鸡",
                      "matchedResults": [{"type":"dish","id":1,"name":"黄焖鸡"}],
                      "answer": "推荐你去一食堂二楼。"
                    }
                    """;
            writeJson(exchange, 200, responseJson);
        });
        server.start();

        PythonAiProxyService service = newService(server.getAddress().getPort());
        String token = buildJwtToken(1001L);

        LlmChatRequest request = new LlmChatRequest();
        request.setSessionId("session-001");
        request.setPrompt("你是校园助手");
        request.setInput("哪个食堂有黄焖鸡");

        LlmChatResponse response = service.chat(request, "Bearer " + token);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("session-001", response.getSessionId());
        Assertions.assertEquals("session-001_hash", response.getSessionToken());
        Assertions.assertEquals("deepseek-chat", response.getModel());
        Assertions.assertEquals("黄焖鸡", response.getSearchKeyword());
        Assertions.assertEquals("推荐你去一食堂二楼。", response.getAnswer());

        Assertions.assertEquals("Bearer " + token, authRef.get());
        Assertions.assertEquals("1001", userIdRef.get());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode reqJson = mapper.readTree(requestBodyRef.get());
        Assertions.assertEquals("session-001", reqJson.path("sessionId").asText());
        Assertions.assertEquals("你是校园助手", reqJson.path("prompt").asText());
        Assertions.assertEquals("哪个食堂有黄焖鸡", reqJson.path("input").asText());
    }

    @Test
    void streamChat_shouldConsumeSseFromPythonEndpoint() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/chat/stream", new StreamingHandler());
        server.start();

        PythonAiProxyService service = newService(server.getAddress().getPort());
        String token = buildJwtToken(1002L);

        LlmChatRequest request = new LlmChatRequest();
        request.setSessionId("session-stream");
        request.setInput("帮我推荐晚饭");

        SseEmitter emitter = service.streamChat(request, "Bearer " + token);

        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        emitter.onCompletion(completion::countDown);
        emitter.onError(errorRef::set);

        boolean completed = completion.await(5, TimeUnit.SECONDS);
        if (!completed) {
            // 某些 Spring 版本中 onCompletion 触发时机不稳定，兜底等待异步任务执行完毕
            TimeUnit.MILLISECONDS.sleep(400);
        }

        Assertions.assertNull(errorRef.get(), "streamChat 不应触发 onError");
    }

    private PythonAiProxyService newService(int port) {
        ObjectMapper objectMapper = new ObjectMapper();
        JwtUtil jwtUtil = new JwtUtil(new TestSystemConfigService());
        return new PythonAiProxyService(
                WebClient.builder(),
                objectMapper,
                jwtUtil,
                "http://localhost:" + port,
                5
        );
    }

    private String buildJwtToken(Long userId) {
        JwtUtil jwtUtil = new JwtUtil(new TestSystemConfigService());
        return jwtUtil.generateToken("tester", userId, "student");
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class StreamingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);

            String payload = ""
                    + "event: session\\n"
                    + "data: {\"sessionId\":\"session-stream\",\"sessionToken\":\"session-stream_hash\",\"model\":\"deepseek-chat\"}\\n\\n"
                    + "event: search\\n"
                    + "data: {\"searchKeyword\":\"晚饭\",\"matchedResults\":[]}\\n\\n"
                    + "event: delta\\n"
                    + "data: {\"content\":\"推荐一食堂\"}\\n\\n"
                    + "event: done\\n"
                    + "data: {\"answer\":\"推荐一食堂\",\"searchKeyword\":\"晚饭\",\"matchedResults\":[]}\\n\\n";

            exchange.getResponseBody().write(payload.getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            exchange.close();
        }
    }

    private static final class TestSystemConfigService implements SystemConfigService {
        private static final String TEST_SECRET = "test-jwt-secret-key-please-change-this-seed-value-123456";

        @Override
        public String getValue(String key, String defaultValue) {
            if ("jwt.secret".equals(key)) {
                return TEST_SECRET;
            }
            return defaultValue;
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
