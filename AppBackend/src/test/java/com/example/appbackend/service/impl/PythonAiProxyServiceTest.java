package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.SystemConfig;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
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
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        AtomicReference<String> cacheStatsAuthRef = new AtomicReference<>();
        AtomicReference<String> cacheClearMethodRef = new AtomicReference<>();

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
        server.createContext("/internal/rag/tool-cache/stats", exchange -> {
            cacheStatsAuthRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, 200, """
                    {
                      "enabled": true,
                      "requestCount": 2,
                      "hitCount": 1,
                      "hitRate": 0.5
                    }
                    """);
        });
        server.createContext("/internal/rag/tool-cache", exchange -> {
            cacheClearMethodRef.set(exchange.getRequestMethod());
            writeJson(exchange, 200, """
                    {
                      "cleared": 1,
                      "entryCount": 0
                    }
                    """);
        });
        server.start();

        PythonAiProxyService service = newService(server.getAddress().getPort());
        String token = buildJwtToken(1004L);

        Object framework = service.getRagFramework("Bearer " + token);
        Object agents = service.getRagAgents("Bearer " + token);
        Object cacheStats = service.getToolCacheStats("Bearer " + token);
        Object cacheClear = service.clearToolCache("Bearer " + token);

        Assertions.assertInstanceOf(Map.class, framework);
        Assertions.assertInstanceOf(Map.class, agents);
        Assertions.assertInstanceOf(Map.class, cacheStats);
        Assertions.assertInstanceOf(Map.class, cacheClear);
        Assertions.assertEquals("Bearer " + token, frameworkAuthRef.get());
        Assertions.assertEquals("Bearer " + token, agentsAuthRef.get());
        Assertions.assertEquals("Bearer " + token, cacheStatsAuthRef.get());
        Assertions.assertEquals("DELETE", cacheClearMethodRef.get());
        Assertions.assertTrue(((Map<?, ?>) framework).containsKey("runtimeFolders"));
        Assertions.assertEquals(1, ((Number) ((Map<?, ?>) agents).get("total")).intValue());
        Assertions.assertEquals(1, ((Number) ((Map<?, ?>) cacheStats).get("hitCount")).intValue());
    }

    @Test
    void questionGenerationCatalog_shouldParseAgentsAndActiveModelBindings() throws Exception {
        startAgentsServer("""
                {"agents":[{"name":"choice_agent","role":"选择题专家"}]}
                """);
        SystemConfigRepository repository = repositoryWith(
                List.of(),
                List.of(systemConfig("ai.agent-bindings.choice_agent.model", " ai.service.text.choice "))
        );

        Map<String, PythonAiProxyService.AgentDescriptor> catalog = newService(
                server.getAddress().getPort(), new TestSystemConfigService(), repository)
                .getQuestionGenerationAgentCatalog("Bearer " + buildJwtToken(2001L));

        Assertions.assertEquals(
                new PythonAiProxyService.AgentDescriptor(
                        "choice_agent", "选择题专家", true, "ai.service.text.choice"),
                catalog.get("choice_agent")
        );
    }

    @Test
    void questionGenerationCatalog_shouldMergeDisabledAgentToggle() throws Exception {
        startAgentsServer("""
                {"agents":[{"name":"choice_agent","role":"选择题专家","enabled":true}]}
                """);
        SystemConfigRepository repository = repositoryWith(
                List.of(systemConfig("ai.agent-enabled.choice_agent", "false")),
                List.of(systemConfig("ai.agent-bindings.choice_agent.model", "ai.service.text.choice"))
        );

        PythonAiProxyService.AgentDescriptor descriptor = newService(
                server.getAddress().getPort(), new TestSystemConfigService(), repository)
                .getQuestionGenerationAgentCatalog("Bearer " + buildJwtToken(2002L))
                .get("choice_agent");

        Assertions.assertFalse(descriptor.enabled());
    }

    @Test
    void questionGenerationCatalog_shouldIgnoreInactiveModelBinding() throws Exception {
        startAgentsServer("""
                {"agents":[{"name":"choice_agent","role":"选择题专家"}]}
                """);
        SystemConfig inactive = systemConfig("ai.agent-bindings.choice_agent.model", "ai.service.text.choice");
        inactive.setStatus(0);

        PythonAiProxyService.AgentDescriptor descriptor = newService(
                server.getAddress().getPort(), new TestSystemConfigService(),
                repositoryWith(List.of(), List.of(inactive)))
                .getQuestionGenerationAgentCatalog("Bearer " + buildJwtToken(2003L))
                .get("choice_agent");

        Assertions.assertNull(descriptor.modelBinding());
    }

    @Test
    void questionGenerationCatalog_shouldTreatBlankModelBindingAsMissing() throws Exception {
        startAgentsServer("""
                {"agents":[{"name":"choice_agent","role":"选择题专家"}]}
                """);

        PythonAiProxyService.AgentDescriptor descriptor = newService(
                server.getAddress().getPort(), new TestSystemConfigService(),
                repositoryWith(List.of(), List.of(systemConfig(
                        "ai.agent-bindings.choice_agent.model", "   "))))
                .getQuestionGenerationAgentCatalog("Bearer " + buildJwtToken(2004L))
                .get("choice_agent");

        Assertions.assertNull(descriptor.modelBinding());
    }

    @Test
    void questionGenerationCatalog_shouldReturnEmptyCatalogForMalformedOrMissingAgents() throws Exception {
        startAgentsServer("{\"agents\":{\"name\":\"not-a-list\"}}");
        PythonAiProxyService service = newService(server.getAddress().getPort());

        Assertions.assertTrue(service.getQuestionGenerationAgentCatalog(
                "Bearer " + buildJwtToken(2005L)).isEmpty());

        server.stop(0);
        server = null;
        startAgentsServer("{\"total\":0}");

        Assertions.assertTrue(newService(server.getAddress().getPort())
                .getQuestionGenerationAgentCatalog("Bearer " + buildJwtToken(2006L)).isEmpty());
    }

    @Test
    void queryQuestionGeneration_shouldExtractAnswerAndOmitNullMaximum() throws Exception {
        AtomicReference<String> requestBodyRef = new AtomicReference<>();
        startQuestionGenerationServer(exchange -> {
            requestBodyRef.set(readBody(exchange));
            writeJson(exchange, 200, "{\"answer\":\"{\\\"questions\\\":[]}\"}");
        });

        String answer = newService(server.getAddress().getPort()).queryQuestionGeneration(
                new PythonAiProxyService.QuestionGenerationPayload(
                        "choice_agent", "依据材料出题", null, "hard"),
                "Bearer " + buildJwtToken(2010L));

        Assertions.assertEquals("{\"questions\":[]}", answer);
        JsonNode request = new ObjectMapper().readTree(requestBodyRef.get());
        Assertions.assertEquals("choice_agent", request.path("agentName").asText());
        Assertions.assertEquals("依据材料出题", request.path("input").asText());
        Assertions.assertEquals("hard", request.path("difficulty").asText());
        Assertions.assertTrue(request.path("maxQuestions").isMissingNode());
    }

    @Test
    void queryQuestionGeneration_shouldAcceptJsonStringResponse() throws Exception {
        startQuestionGenerationServer(exchange -> writeJson(exchange, 200, "\"{\\\"questions\\\":[]}\""));

        String answer = newService(server.getAddress().getPort()).queryQuestionGeneration(
                new PythonAiProxyService.QuestionGenerationPayload(
                        "choice_agent", "依据材料出题", 2, null),
                "Bearer " + buildJwtToken(2011L));

        Assertions.assertEquals("{\"questions\":[]}", answer);
    }

    @Test
    void queryQuestionGeneration_shouldRejectMissingAnswerWithStableError() throws Exception {
        startQuestionGenerationServer(exchange -> writeJson(exchange, 200, "{\"documents\":[]}"));

        BusinessException error = Assertions.assertThrows(BusinessException.class, () ->
                newService(server.getAddress().getPort()).queryQuestionGeneration(
                        new PythonAiProxyService.QuestionGenerationPayload(
                                "choice_agent", "依据材料出题", 2, null),
                        "Bearer " + buildJwtToken(2012L)));

        Assertions.assertEquals("Python AI 服务未返回题库生成答案", error.getMessage());
    }

    @Test
    void queryQuestionGeneration_shouldRejectNonStringAnswerWithStableError() throws Exception {
        startQuestionGenerationServer(exchange -> writeJson(exchange, 200, "{\"answer\":{\"questions\":[]}}"));

        BusinessException error = Assertions.assertThrows(BusinessException.class, () ->
                newService(server.getAddress().getPort()).queryQuestionGeneration(
                        new PythonAiProxyService.QuestionGenerationPayload(
                                "choice_agent", "依据材料出题", 2, null),
                        "Bearer " + buildJwtToken(2013L)));

        Assertions.assertEquals("Python AI 服务未返回题库生成答案", error.getMessage());
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
    void sseEventNamesRejectLogInjectionAndPayloadFragments() {
        Assertions.assertEquals("generation_start", PythonAiProxyService.safeSseEventName("generation_start"));
        Assertions.assertEquals("message", PythonAiProxyService.safeSseEventName(
                "done\ndata: {\"internalCapability\":\"secret-capability\"}"));
        Assertions.assertEquals("message", PythonAiProxyService.safeSseEventName(""));
    }

    @Test
    void generatedExportDownloadSendsOnlyPersistedCapabilityAndReturnsBinaryHeaders() throws Exception {
        byte[] payload = new byte[]{0, -1, 1, 2, 0, 127};
        AtomicReference<String> pathRef = new AtomicReference<>();
        AtomicReference<String> capabilityRef = new AtomicReference<>();
        AtomicReference<String> authorizationRef = new AtomicReference<>();
        AtomicReference<String> userIdRef = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/exports/export.bin", exchange -> {
            pathRef.set(exchange.getRequestURI().getRawPath());
            capabilityRef.set(exchange.getRequestHeaders().getFirst("X-AI-Export-Capability"));
            authorizationRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            userIdRef.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_PDF_VALUE);
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=upstream-secret.bin");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        PythonAiProxyService.GeneratedExportResponse response = newService(server.getAddress().getPort())
                .downloadGeneratedExport("export.bin", "persisted-capability");

        Assertions.assertArrayEquals(payload, response.bytes());
        Assertions.assertEquals(MediaType.APPLICATION_PDF, response.contentType());
        Assertions.assertEquals(payload.length, response.declaredLength());
        Assertions.assertEquals("/internal/rag/exports/export.bin", pathRef.get());
        Assertions.assertEquals("persisted-capability", capabilityRef.get());
        Assertions.assertNull(authorizationRef.get());
        Assertions.assertNull(userIdRef.get());
    }

    @Test
    void generatedExportDownloadRejectsDeclaredLengthAboveConfiguredMaximum() throws Exception {
        byte[] payload = "oversized".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/exports/declared.bin", exchange -> {
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> newService(server.getAddress().getPort(), 4)
                        .downloadGeneratedExport("declared.bin", "capability")
        );

        Assertions.assertEquals(413, error.getCode());
    }

    @Test
    void generatedExportDownloadRejectsChunkedBodyAboveConfiguredMaximum() throws Exception {
        byte[] payload = "chunked-body".getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/exports/chunked.bin", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> newService(server.getAddress().getPort(), 4)
                        .downloadGeneratedExport("chunked.bin", "capability")
        );

        Assertions.assertEquals(413, error.getCode());
    }

    @Test
    void generatedExportDownloadPreservesUpstreamGone() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/exports/gone.bin", exchange -> {
            exchange.sendResponseHeaders(410, -1);
            exchange.close();
        });
        server.start();

        BusinessException error = Assertions.assertThrows(
                BusinessException.class,
                () -> newService(server.getAddress().getPort())
                        .downloadGeneratedExport("gone.bin", "capability")
        );

        Assertions.assertEquals(410, error.getCode());
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

    private PythonAiProxyService newService(int port, int maxBytes) {
        return newService(port, new TestSystemConfigService(),
                newSystemConfigRepository(new TestSystemConfigService()), maxBytes);
    }

    private PythonAiProxyService newService(int port, SystemConfigService systemConfigService) {
        return newService(port, systemConfigService, newSystemConfigRepository(systemConfigService));
    }

    private PythonAiProxyService newService(int port, SystemConfigService systemConfigService,
                                             SystemConfigRepository systemConfigRepository) {
        return newService(port, systemConfigService, systemConfigRepository, 1024 * 1024);
    }

    private PythonAiProxyService newService(int port, SystemConfigService systemConfigService,
                                             SystemConfigRepository systemConfigRepository,
                                             int maxBytes) {
        ObjectMapper objectMapper = new ObjectMapper();
        JwtUtil jwtUtil = new JwtUtil(systemConfigService);
        return new PythonAiProxyService(
                WebClient.builder(),
                objectMapper,
                jwtUtil,
                systemConfigService,
                systemConfigRepository,
                "http://localhost:" + port,
                5,
                maxBytes
        );
    }

    private void startAgentsServer(String responseJson) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/agents", exchange -> writeJson(exchange, 200, responseJson));
        server.start();
    }

    private void startQuestionGenerationServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/query", handler);
        server.start();
    }

    private SystemConfigRepository repositoryWith(List<SystemConfig> agentToggles,
                                                  List<SystemConfig> modelBindings) {
        return (SystemConfigRepository) Proxy.newProxyInstance(
                SystemConfigRepository.class.getClassLoader(),
                new Class<?>[]{SystemConfigRepository.class},
                (proxy, method, args) -> {
                    if ("findByConfigKeyStartingWithAndStatus".equals(method.getName())) {
                        String prefix = String.valueOf(args[0]);
                        int status = (Integer) args[1];
                        List<SystemConfig> source = "ai.agent-enabled.".equals(prefix)
                                ? agentToggles
                                : "ai.agent-bindings.".equals(prefix) ? modelBindings : List.of();
                        return source.stream().filter(config -> Integer.valueOf(status).equals(config.getStatus())).toList();
                    }
                    if ("toString".equals(method.getName())) {
                        return "QuestionGenerationCatalogRepository";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private SystemConfigRepository newSystemConfigRepository(SystemConfigService systemConfigService) {
        return (SystemConfigRepository) Proxy.newProxyInstance(
                SystemConfigRepository.class.getClassLoader(),
                new Class<?>[]{SystemConfigRepository.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("toString".equals(methodName)) {
                        return "TestSystemConfigRepository";
                    }
                    if ("hashCode".equals(methodName)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(methodName)) {
                        return proxy == args[0];
                    }
                    if ("findByConfigKeyStartingWithAndStatus".equals(methodName)) {
                        String prefix = String.valueOf(args[0]);
                        if ("ai.agent-enabled.".equals(prefix)) {
                            return List.of();
                        }
                        if ("ai.tool-enabled.".equals(prefix)) {
                            return List.of();
                        }
                        if ("ai.agent-bindings.".equals(prefix)) {
                            return List.of(
                                    systemConfig("ai.agent-bindings.leader_agent.model", systemConfigService.getValue("ai.agent-bindings.leader_agent.model", "")),
                                    systemConfig("ai.agent-bindings.ppt_outline_agent.model", systemConfigService.getValue("ai.agent-bindings.ppt_outline_agent.model", "")),
                                    systemConfig("ai.agent-bindings.diagram_flowchart_agent.model", systemConfigService.getValue("ai.agent-bindings.diagram_flowchart_agent.model", ""))
                            );
                        }
                        return List.of();
                    }
                    if ("findByConfigKeyStartingWith".equals(methodName)) {
                        return List.of();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static SystemConfig systemConfig(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setStatus(1);
        return config;
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0F;
        }
        if (double.class.equals(returnType)) {
            return 0D;
        }
        return null;
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
