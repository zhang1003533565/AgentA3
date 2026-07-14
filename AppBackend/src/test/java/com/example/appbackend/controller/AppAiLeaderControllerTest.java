package com.example.appbackend.controller;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.AiLeaderGeneratedExport;
import com.example.appbackend.entity.Result;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.repository.AiLeaderGeneratedExportRepository;
import com.example.appbackend.service.UserProfileService;
import com.example.appbackend.service.impl.AssistantEnvelopeService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppAiLeaderControllerTest {

    private PythonAiProxyService pythonAiProxyService;
    private AiLeaderSessionRepository sessionRepository;
    private AiLeaderMessageRepository messageRepository;
    private AiLeaderGeneratedExportRepository exportRepository;
    private UserProfileService userProfileService;
    private ObjectMapper objectMapper;
    private AppAiLeaderController controller;
    private AiLeaderSession session;
    private final List<AiLeaderMessage> savedMessages = new ArrayList<>();
    private final AtomicReference<AiLeaderGeneratedExport> savedExport = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        pythonAiProxyService = mock(PythonAiProxyService.class);
        sessionRepository = mock(AiLeaderSessionRepository.class);
        messageRepository = mock(AiLeaderMessageRepository.class);
        exportRepository = mock(AiLeaderGeneratedExportRepository.class);
        userProfileService = mock(UserProfileService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();

        session = new AiLeaderSession();
        session.setId(9L);
        session.setUserId(42L);
        session.setSessionId("session-1");
        session.setTitle("测试会话");
        session.setLastMessage("");
        session.setMessageCount(0);

        when(sessionRepository.findByUserIdAndSessionId(42L, "session-1"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.countByLeaderSessionId(9L)).thenReturn(2L);
        AtomicLong ids = new AtomicLong(100L);
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            AiLeaderMessage message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(ids.getAndIncrement());
                savedMessages.add(message);
            }
            return message;
        });
        when(userProfileService.buildLeaderProfileContext(anyLong(), any())).thenReturn(Map.of());
        when(exportRepository.findByMessageIdAndStorageKey(anyLong(), any())).thenAnswer(invocation -> {
            AiLeaderGeneratedExport current = savedExport.get();
            if (current != null
                    && current.getMessageId().equals(invocation.getArgument(0))
                    && current.getStorageKey().equals(invocation.getArgument(1))) {
                return Optional.of(current);
            }
            return Optional.empty();
        });
        when(exportRepository.save(any())).thenAnswer(invocation -> {
            AiLeaderGeneratedExport value = invocation.getArgument(0);
            savedExport.set(value);
            return value;
        });

        AssistantEnvelopeService assistantEnvelopeService = new AssistantEnvelopeService(
                messageRepository, exportRepository, objectMapper);

        controller = new AppAiLeaderController(
                pythonAiProxyService,
                sessionRepository,
                messageRepository,
                userProfileService,
                objectMapper,
                assistantEnvelopeService
        );
    }

    @Test
    void syncPersistsPublicEnvelopeWithRealMessageIdAndNeverLeaksCapability() throws Exception {
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(validGeneratedResponse());

        LlmChatRequest request = new LlmChatRequest();
        request.setSessionId("session-1");
        request.setInput("导出复习资料");
        request.setLlmModel("ai.service.text.test");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setAttribute("userId", 42L);
        httpRequest.addHeader("Authorization", "Bearer test-token");

        Result<LlmChatResponse> result = controller.query(request, httpRequest);
        JsonNode response = objectMapper.valueToTree(result.getData());
        String serialized = objectMapper.writeValueAsString(result.getData());
        AiLeaderMessage assistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole()))
                .findFirst()
                .orElseThrow();

        assertThat(response.path("messageId").asLong()).isEqualTo(101L);
        assertThat(response.path("resources").path(0).path("messageId").asLong()).isEqualTo(101L);
        assertThat(response.path("resources").path(0).path("url").asText())
                .isEqualTo("/api/ai/leader/sessions/session-1/messages/101/exports/123e4567-e89b-12d3-a456-426614174000.docx");
        assertThat(response.path("attachments").path(0).path("url").asText())
                .isEqualTo(response.path("resources").path(0).path("url").asText());
        assertThat(serialized).doesNotContain("internalCapability", "secret-capability", "localhost");
        assertThat(assistant.getAttachmentsJson()).doesNotContain("internalCapability", "secret-capability");
        assertThat(assistant.getResourcesJson()).contains("res_doc").doesNotContain("secret-capability");
        assertThat(assistant.getMatchedResultsJson()).contains("课程资料").doesNotContain("phone", "token");
        assertThat(assistant.getEvidenceChainJson()).contains("assistant-evidence-v1");
        assertThat(savedExport.get()).isNotNull();
        assertThat(savedExport.get().getPythonCapability()).isEqualTo("secret-capability");
        assertThat(savedExport.get())
                .extracting(AiLeaderGeneratedExport::getUserId,
                        AiLeaderGeneratedExport::getLeaderSessionId,
                        AiLeaderGeneratedExport::getMessageId,
                        AiLeaderGeneratedExport::getResourceId)
                .containsExactly(42L, 9L, 101L, "res_doc");
        verify(exportRepository, times(1)).save(any());
    }

    @Test
    void streamGenerationStartAndDoneReuseMessageIdAndExposeOnlyPublicEnvelope() throws Exception {
        AtomicReference<Map<String, Object>> generationStart = new AtomicReference<>();
        AtomicReference<Map<String, Object>> done = new AtomicReference<>();
        when(pythonAiProxyService.streamRag(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<String, Object> consumer = invocation.getArgument(2);
            Map<String, Object> first = validGeneratedResponse();
            Map<String, Object> last = validGeneratedResponse();
            generationStart.set(first);
            done.set(last);
            consumer.accept("generation_start", first);
            consumer.accept("done", last);
            return new SseEmitter();
        });

        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());

        assertThat(generationStart.get().get("messageId")).isEqualTo(101L);
        assertThat(done.get().get("messageId")).isEqualTo(101L);
        assertThat(objectMapper.writeValueAsString(done.get()))
                .contains("/sessions/session-1/messages/101/exports/")
                .doesNotContain("secret-capability", "internalCapability", "localhost");
        assertThat(savedMessages.stream().filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())))
                .hasSize(1);
        verify(exportRepository, times(1)).save(any());
    }

    @Test
    void historyRestoresTheSameEnvelopeAndMarksMalformedEvidence() throws Exception {
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(validGeneratedResponse());
        controller.query(request(), authenticatedRequest());
        AiLeaderMessage assistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole()))
                .findFirst().orElseThrow();
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(assistant));

        JsonNode validHistory = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());
        assertThat(validHistory.path("messages").path(0).path("resources").path(0).path("messageId").asLong())
                .isEqualTo(101L);
        assertThat(validHistory.path("messages").path(0).path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("available");

        assistant.setEvidenceChainJson("{malformed");
        JsonNode malformedHistory = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());
        assertThat(malformedHistory.path("messages").path(0).path("content").asText()).isEqualTo("资料已生成");
        assertThat(malformedHistory.path("messages").path(0).path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("malformed");
        assertThat(malformedHistory.path("messages").path(0).path("evidenceChain").path("sources")).isEmpty();
    }

    @Test
    void historyRejectsIntegrityMismatchWithoutHidingTheAnswer() throws Exception {
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(validGeneratedResponse());
        controller.query(request(), authenticatedRequest());
        AiLeaderMessage assistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole()))
                .findFirst().orElseThrow();
        ObjectNode chain = (ObjectNode) objectMapper.readTree(assistant.getEvidenceChainJson());
        chain.put("answerDigest", "sha256:" + "f".repeat(64));
        assistant.setEvidenceChainJson(objectMapper.writeValueAsString(chain));
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(assistant));

        JsonNode history = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());
        assertThat(history.path("messages").path(0).path("content").asText()).isEqualTo("资料已生成");
        assertThat(history.path("messages").path(0).path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("integrity_failed");
        assertThat(history.path("messages").path(0).path("evidenceChain").path("sources")).isEmpty();
    }

    @Test
    void invalidTypedResourceFailsClosedAndCannotCreateExportManifest() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) ((List<?>) raw.get("resources")).getFirst();
        resource.put("id", "../forged-resource");
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        JsonNode response = objectMapper.valueToTree(result.getData());

        assertThat(response.path("answer").asText()).isEqualTo("资料已生成");
        assertThat(response.path("resources")).isEmpty();
        assertThat(response.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("generation_failed");
        assertThat(objectMapper.writeValueAsString(result.getData()))
                .doesNotContain("secret-capability", "internalCapability");
        assertThat(savedExport.get()).isNull();
    }

    @Test
    void matchedResultsAreCappedAndRecursivelyStripSensitiveMetadata() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        List<Map<String, Object>> documents = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            documents.add(Map.of(
                    "id", "doc-" + index,
                    "content", "课".repeat(900),
                    "score", 0.8,
                    "metadata", Map.of(
                            "title", "课程资料 " + index,
                            "location", "教学楼",
                            "phone", "13800000000",
                            "raw", Map.of("token", "secret"))));
        }
        raw.put("documents", documents);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        JsonNode response = objectMapper.valueToTree(result.getData());

        assertThat(response.path("matchedResults")).hasSize(20);
        assertThat(response.path("matchedResults").path(0).path("content").asText()).hasSize(800);
        assertThat(response.path("matchedResults").toString())
                .contains("教学楼")
                .doesNotContain("13800000000", "phone", "raw", "secret", "token");
    }

    @Test
    void legacyPythonStaticUrlsRemainVisibleOnlyAsUnavailableMetadata() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = new LinkedHashMap<>((Map<String, Object>) ((List<?>) raw.get("resources")).getFirst());
        resource.put("storageKey", "");
        resource.put("url", "http://localhost:8081/generated/old.docx");
        AiLeaderMessage message = new AiLeaderMessage();
        message.setId(77L);
        message.setLeaderSessionId(9L);
        message.setRole(AiLeaderMessage.ROLE_ASSISTANT);
        message.setContent("旧回答仍可阅读");
        message.setResourcesJson(objectMapper.writeValueAsString(List.of(resource)));
        message.setAttachmentsJson(objectMapper.writeValueAsString(List.of(Map.of(
                "name", "old.docx", "url", "http://localhost:8081/generated/old.docx"))));
        message.setMatchedResultsJson("[]");
        message.setEvidenceChainJson("");
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(message));

        JsonNode history = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());
        JsonNode restored = history.path("messages").path(0);

        assertThat(restored.path("content").asText()).isEqualTo("旧回答仍可阅读");
        assertThat(restored.path("resources").path(0).path("availability").asText())
                .isEqualTo("legacy_unavailable");
        assertThat(restored.path("resources").path(0).path("url").asText()).isEmpty();
        assertThat(restored.path("attachments").path(0).path("status").asText())
                .isEqualTo("legacy_unavailable");
        assertThat(restored.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("legacy_missing");
        assertThat(restored.toString()).doesNotContain("localhost", "/generated/");
    }

    private LlmChatRequest request() {
        LlmChatRequest request = new LlmChatRequest();
        request.setSessionId("session-1");
        request.setInput("导出复习资料");
        request.setLlmModel("ai.service.text.test");
        return request;
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 42L);
        request.addHeader("Authorization", "Bearer test-token");
        return request;
    }

    private Map<String, Object> validGeneratedResponse() throws Exception {
        String digest = "a".repeat(64);
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("schemaVersion", "assistant-resource-v1");
        resource.put("id", "res_doc");
        resource.put("messageId", null);
        resource.put("kind", "document");
        resource.put("deliveryType", "document");
        resource.put("groundingStatus", "model_only");
        resource.put("title", "复习资料.docx");
        resource.put("summary", "本轮生成的复习资料");
        resource.put("mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        resource.put("storageKey", "123e4567-e89b-12d3-a456-426614174000.docx");
        resource.put("url", "");
        resource.put("previewUrl", "");
        resource.put("sourceType", "generated_export");
        resource.put("sourceId", "agent_generated_file");
        resource.put("evidenceIds", List.of());
        resource.put("actions", List.of(Map.of(
                "type", "download", "label", "下载", "target", "resource", "requiresAuth", true)));
        resource.put("authScope", "session_owner");
        resource.put("createdAt", "2026-07-14T12:00:00Z");
        resource.put("expiresAt", "2026-07-21T12:00:00Z");
        resource.put("integrity", Map.of("algorithm", "SHA-256", "digest", digest, "size", 12));
        resource.put("payload", Map.of("type", "file", "format", "docx", "size", 12, "digest", "sha256:" + digest));
        resource.put("metadata", Map.of("serverGenerated", true));

        Map<String, Object> chain = new LinkedHashMap<>();
        chain.put("schemaVersion", "assistant-evidence-v1");
        chain.put("chainId", "chain_test");
        chain.put("requestId", "req_test");
        chain.put("status", "model_only");
        chain.put("generatedAt", "2026-07-14T12:00:00Z");
        chain.put("evidenceState", "available");
        chain.put("queryDigest", "sha256:" + "b".repeat(64));
        chain.put("answerDigest", "sha256:" + "c".repeat(64));
        chain.put("sources", List.of());
        chain.put("steps", List.of(Map.of("stage", "generation", "detail", Map.of())));
        chain.put("resourceLinks", List.of(Map.of("resourceId", "res_doc", "evidenceIds", List.of())));
        chain.put("generation", Map.of(
                "agent", "leader_agent", "model", "configured-model", "answerType", "document", "profileContextUsed", false));
        chain.put("integrity", Map.of(
                "algorithm", "SHA-256",
                "digest", canonicalDigest(chain),
                "scope", "canonical-json-without-integrity",
                "signed", false));

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("name", "复习资料.docx");
        attachment.put("fileName", "复习资料.docx");
        attachment.put("type", "docx");
        attachment.put("mimeType", resource.get("mimeType"));
        attachment.put("storageKey", resource.get("storageKey"));
        attachment.put("serverGenerated", true);
        attachment.put("internalCapability", "secret-capability");
        attachment.put("sha256", digest);
        attachment.put("size", 12);
        attachment.put("createdAt", "2026-07-14T12:00:00Z");
        attachment.put("expiresAt", "2026-07-21T12:00:00Z");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", "资料已生成");
        response.put("answerType", "document");
        response.put("outputType", "document");
        response.put("outputTypes", List.of("text", "document"));
        response.put("attachments", List.of(attachment));
        response.put("resources", List.of(resource));
        response.put("evidenceChain", chain);
        response.put("documents", List.of(Map.of(
                "id", "doc-1",
                "content", "课程资料",
                "score", 0.9,
                "metadata", Map.of("title", "资料", "phone", "13800000000", "token", "hidden"))));
        return response;
    }

    private String canonicalDigest(Map<String, Object> value) throws Exception {
        Object canonical = canonicalize(value);
        byte[] bytes = objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalize).toList();
        }
        return value;
    }
}
