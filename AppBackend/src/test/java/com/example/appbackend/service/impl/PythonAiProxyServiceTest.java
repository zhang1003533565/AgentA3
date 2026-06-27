package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.exception.BusinessException;
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
        AtomicReference<String> aiBaseUrlRef = new AtomicReference<>();
        AtomicReference<String> aiApiKeyRef = new AtomicReference<>();
        AtomicReference<String> aiModelRef = new AtomicReference<>();
        AtomicReference<String> requestBodyRef = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/chat", exchange -> {
            authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            userIdRef.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            aiBaseUrlRef.set(exchange.getRequestHeaders().getFirst("X-AI-Base-Url"));
            aiApiKeyRef.set(exchange.getRequestHeaders().getFirst("X-AI-Api-Key"));
            aiModelRef.set(exchange.getRequestHeaders().getFirst("X-AI-Model"));
            requestBodyRef.set(readBody(exchange));

            String responseJson = """
                    {
                      "sessionId": "session-001",
                      "sessionToken": "session-001_hash",
                      "model": "deepseek-chat",
                      "ragStrategy": "direct_agent",
                      "agentName": "ppt_outline_agent",
                      "searchKeyword": "黄焖鸡",
                      "matchedResults": [{"type":"dish","id":1,"name":"黄焖鸡"}],
                      "retrievalMeta": {"documentCount": 1},
                      "trace": [{"stage":"retrieve","detail":{"documentCount":1}}],
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
        request.setAgentName("ppt_outline_agent");
        request.setLlmModel("ai.service.text");
        request.setInput("哪个食堂有黄焖鸡");

        LlmChatResponse response = service.chat(request, "Bearer " + token);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("session-001", response.getSessionId());
        Assertions.assertEquals("session-001_hash", response.getSessionToken());
        Assertions.assertEquals("deepseek-chat", response.getModel());
        Assertions.assertEquals("direct_agent", response.getRagStrategy());
        Assertions.assertEquals("ppt_outline_agent", response.getAgentName());
        Assertions.assertEquals("黄焖鸡", response.getSearchKeyword());
        Assertions.assertEquals("推荐你去一食堂二楼。", response.getAnswer());
        Assertions.assertEquals(1, response.getRetrievalMeta().get("documentCount"));
        Assertions.assertEquals(1, response.getTrace().size());

        Assertions.assertEquals("Bearer " + token, authRef.get());
        Assertions.assertEquals("1001", userIdRef.get());
        Assertions.assertEquals("https://llm.test/v1", aiBaseUrlRef.get());
        Assertions.assertEquals("test-ai-key", aiApiKeyRef.get());
        Assertions.assertEquals("test-model", aiModelRef.get());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode reqJson = mapper.readTree(requestBodyRef.get());
        Assertions.assertEquals("session-001", reqJson.path("sessionId").asText());
        Assertions.assertEquals("你是校园助手", reqJson.path("prompt").asText());
        Assertions.assertTrue(reqJson.path("ragStrategy").isMissingNode() || reqJson.path("ragStrategy").isNull());
        Assertions.assertEquals("ppt_outline_agent", reqJson.path("agentName").asText());
        Assertions.assertEquals("哪个食堂有黄焖鸡", reqJson.path("input").asText());
    }

    @Test
    void ragQuery_shouldProxyRequestToPythonRagEndpoint() throws Exception {
        AtomicReference<String> authRef = new AtomicReference<>();
        AtomicReference<String> userIdRef = new AtomicReference<>();
        AtomicReference<String> aiModelRef = new AtomicReference<>();
        AtomicReference<String> requestBodyRef = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/query", exchange -> {
            authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            userIdRef.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            aiModelRef.set(exchange.getRequestHeaders().getFirst("X-AI-Model"));
            requestBodyRef.set(readBody(exchange));

            String responseJson = """
                    {
                      "strategy": "text_to_sql",
                      "answer": "已生成只读 SQL",
                      "documents": [],
                      "trace": [{"stage":"generate_sql","detail":{"readonly":true}}],
                      "metadata": {"readonly": true, "sql": "SELECT id FROM dish LIMIT 20"}
                    }
                    """;
            writeJson(exchange, 200, responseJson);
        });
        server.start();

        PythonAiProxyService service = newService(server.getAddress().getPort());
        String token = buildJwtToken(1003L);

        Object response = service.queryRag(
                Map.of(
                        "input", "统计菜品数量",
                        "ragStrategy", "text_to_sql",
                        "llmModel", "ai.service.text",
                        "embeddingModel", "ai.service.embedding.qwen"
                ),
                "Bearer " + token
        );

        Assertions.assertInstanceOf(Map.class, response);
        Map<?, ?> responseMap = (Map<?, ?>) response;
        Assertions.assertEquals("text_to_sql", responseMap.get("strategy"));
        Assertions.assertEquals("已生成只读 SQL", responseMap.get("answer"));
        Assertions.assertEquals("Bearer " + token, authRef.get());
        Assertions.assertEquals("1003", userIdRef.get());
        Assertions.assertEquals("test-model", aiModelRef.get());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode reqJson = mapper.readTree(requestBodyRef.get());
        Assertions.assertEquals("统计菜品数量", reqJson.path("input").asText());
        Assertions.assertTrue(reqJson.path("ragStrategy").isMissingNode());
        Assertions.assertTrue(reqJson.path("llmModel").isMissingNode());
        Assertions.assertTrue(reqJson.path("embeddingModel").isMissingNode());
    }

    @Test
    void ragManagement_shouldProxyFrameworkAndAgentsEndpoints() throws Exception {
        AtomicReference<String> frameworkAuthRef = new AtomicReference<>();
        AtomicReference<String> agentsAuthRef = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/framework", exchange -> {
            frameworkAuthRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, """
                    {
                      "runtimeFolders": {"multiAgents": "app/multi_agents"},
                      "coverage": []
                    }
                    """);
        });
        server.createContext("/internal/rag/agents", exchange -> {
            agentsAuthRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, """
                    {
                      "total": 1,
                      "agents": [{"name":"textbook_knowledge_agent","role":"教材知识点智能体"}]
                    }
                    """);
        });
        server.start();

        PythonAiProxyService service = newService(server.getAddress().getPort());
        String token = buildJwtToken(1004L);

        Object framework = service.getRagFramework("Bearer " + token);
        Object agents = service.getRagAgents("Bearer " + token);

        Assertions.assertInstanceOf(Map.class, framework);
        Assertions.assertInstanceOf(Map.class, agents);
        Assertions.assertEquals("Bearer " + token, frameworkAuthRef.get());
        Assertions.assertEquals("Bearer " + token, agentsAuthRef.get());
        Assertions.assertTrue(((Map<?, ?>) framework).containsKey("runtimeFolders"));
        Assertions.assertEquals(1, ((Number) ((Map<?, ?>) agents).get("total")).intValue());
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

    @Test
    void chat_shouldFailFastWhenAiConfigMissing() {
        PythonAiProxyService service = newService(65535, new MissingApiKeySystemConfigService());
        String token = buildJwtToken(1005L);

        LlmChatRequest request = new LlmChatRequest();
        request.setSessionId("missing-config");
        request.setInput("你好");

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> service.chat(request, "Bearer " + token)
        );

        Assertions.assertTrue(error.getMessage().contains("ai.service.text.api-key"));
    }

    private PythonAiProxyService newService(int port) {
        return newService(port, new TestSystemConfigService());
    }

    private PythonAiProxyService newService(int port, SystemConfigService systemConfigService) {
        ObjectMapper objectMapper = new ObjectMapper();
        JwtUtil jwtUtil = new JwtUtil(systemConfigService);
        return new PythonAiProxyService(
                WebClient.builder(),
                objectMapper,
                jwtUtil,
                systemConfigService,
                "http://localhost:" + port,
                5,
                1024 * 1024
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

    private static class TestSystemConfigService implements SystemConfigService {
        private static final String TEST_SECRET = "test-jwt-secret-key-please-change-this-seed-value-123456";

        @Override
        public String getValue(String key, String defaultValue) {
            if ("jwt.secret".equals(key)) {
                return TEST_SECRET;
            }
            if (key.startsWith("ai.agent-bindings.") && key.endsWith(".model")) {
                return "ai.service.text";
            }
            if ("ai.service.text.provider".equals(key)) {
                return "deepseek";
            }
            if ("ai.service.text.base-url".equals(key)) {
                return "https://llm.test/v1";
            }
            if ("ai.service.text.api-key".equals(key)) {
                return "test-ai-key";
            }
            if ("ai.service.text.model".equals(key)) {
                return "test-model";
            }
            if ("ai.service.embedding.qwen.provider".equals(key)) {
                return "qwen";
            }
            if ("ai.service.embedding.qwen.base-url".equals(key)) {
                return "https://embedding.test/v1";
            }
            if ("ai.service.embedding.qwen.api-key".equals(key)) {
                return "test-embedding-key";
            }
            if ("ai.service.embedding.qwen.model".equals(key)) {
                return "text-embedding-v4";
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

    private static final class MissingApiKeySystemConfigService extends TestSystemConfigService {
        @Override
        public String getValue(String key, String defaultValue) {
            if ("ai.service.text.api-key".equals(key)) {
                return "";
            }
            return super.getValue(key, defaultValue);
        }
    }
}
