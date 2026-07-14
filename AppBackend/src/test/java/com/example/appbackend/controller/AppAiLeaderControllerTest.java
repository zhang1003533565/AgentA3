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
                messageRepository, exportRepository, objectMapper, "cdn.example.edu");

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
        assertThat(validHistory.path("messages").path(0).path("resources").path(0).path("availability").asText())
                .isEqualTo("active");
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

    @Test
    void authenticatedExternalResourceAndSecretEvidenceStepFailClosed() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("attachments", List.of());
        Map<String, Object> resource = resource(raw);
        resource.put("storageKey", "");
        resource.put("url", "https://attacker.example/collect?token=steal");
        resource.put("authScope", "session_owner");
        resource.put("actions", List.of(Map.of(
                "type", "download", "label", "下载", "target", "resource", "requiresAuth", true)));
        Map<String, Object> chain = evidenceChain(raw);
        chain.put("steps", List.of(Map.of(
                "stage", "tool_result",
                "detail", Map.of("internalCapability", "chain-secret", "endpoint", "http://localhost:8081/internal"))));
        refreshChainIntegrity(chain);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        JsonNode response = objectMapper.valueToTree(result.getData());
        String serialized = objectMapper.writeValueAsString(result.getData());

        assertThat(response.path("resources")).isEmpty();
        assertThat(response.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("generation_failed");
        assertThat(serialized).doesNotContain(
                "attacker.example", "token=steal", "chain-secret", "internalCapability", "localhost");
        assertThat(savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole()))
                .findFirst().orElseThrow().getEvidenceChainJson())
                .doesNotContain("chain-secret", "localhost");
    }

    @Test
    void publicExternalResourceRequiresApprovedHostAndNoAuthAction() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("attachments", List.of());
        Map<String, Object> resource = resource(raw);
        resource.put("storageKey", "");
        resource.put("url", "https://cdn.example.edu/reading.pdf");
        resource.put("authScope", "public");
        resource.put("actions", List.of(Map.of(
                "type", "download", "label", "下载", "target", "resource", "requiresAuth", false)));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        JsonNode response = objectMapper.valueToTree(
                controller.query(request(), authenticatedRequest()).getData());

        assertThat(response.path("resources").path(0).path("url").asText())
                .isEqualTo("https://cdn.example.edu/reading.pdf");
        assertThat(response.path("resources").path(0).path("actions").path(0).path("requiresAuth").asBoolean())
                .isFalse();
    }

    @Test
    void publicExternalResourceRejectsUserInfoNonStandardPortsAndSecretQueries() throws Exception {
        for (String unsafeUrl : List.of(
                "https://user:password@cdn.example.edu/reading.pdf",
                "https://cdn.example.edu:444/reading.pdf",
                "https://cdn.example.edu/reading.pdf?token=secret")) {
            Map<String, Object> raw = validGeneratedResponse();
            raw.put("attachments", List.of());
            Map<String, Object> resource = resource(raw);
            resource.put("storageKey", "");
            resource.put("url", unsafeUrl);
            resource.put("authScope", "public");
            resource.put("actions", List.of(Map.of(
                    "type", "download", "label", "下载", "target", "resource", "requiresAuth", false)));
            when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

            JsonNode response = objectMapper.valueToTree(
                    controller.query(request(), authenticatedRequest()).getData());

            assertThat(response.path("resources"))
                    .as("unsafe public URL must fail closed: %s", unsafeUrl)
                    .isEmpty();
        }
    }

    @Test
    void incompatiblePayloadAndStructuredTextValuesAreRejectedInsteadOfStringified() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("attachments", List.of());
        Map<String, Object> resource = resource(raw);
        resource.put("kind", "course");
        resource.put("deliveryType", "business_card");
        resource.put("payload", Map.of("type", "file", "format", "docx", "size", 12,
                "digest", "sha256:" + "a".repeat(64)));
        resource.put("title", Map.of("internalCapability", "typed-secret"));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        JsonNode response = objectMapper.valueToTree(result.getData());

        assertThat(response.path("resources")).isEmpty();
        assertThat(objectMapper.writeValueAsString(result.getData()))
                .doesNotContain("typed-secret", "internalCapability");
    }

    @Test
    void generatedManifestRejectsStructuredOptionalTextFields() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        @SuppressWarnings("unchecked")
        Map<String, Object> attachment = new LinkedHashMap<>(
                (Map<String, Object>) ((List<?>) raw.get("attachments")).getFirst());
        attachment.put("title", Map.of("internalCapability", "typed-secret"));
        raw.put("attachments", List.of(attachment));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        JsonNode response = objectMapper.valueToTree(result.getData());

        assertThat(response.path("resources").path(0).path("url").asText()).isEmpty();
        assertThat(response.path("resources").path(0).path("availability").asText()).isEqualTo("unavailable");
        assertThat(objectMapper.writeValueAsString(result.getData())).doesNotContain("typed-secret");
        verify(exportRepository, times(0)).save(any());
    }

    @Test
    void repeatedGenerationStartAndDoneAreIdempotent() throws Exception {
        when(pythonAiProxyService.streamRag(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<String, Object> consumer = invocation.getArgument(2);
            consumer.accept("generation_start", validGeneratedResponse());
            consumer.accept("generation_start", validGeneratedResponse());
            consumer.accept("done", validGeneratedResponse());
            consumer.accept("done", validGeneratedResponse());
            return new SseEmitter();
        });
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());

        assertThat(savedMessages.stream().filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())))
                .hasSize(1);
        verify(exportRepository, times(1)).save(any());
        verify(userProfileService, times(1)).addEvidence(anyLong(), any());
    }

    @Test
    void changedManifestBindingOnDoneCannotReuseTheExistingDownloadUrl() throws Exception {
        AtomicReference<Map<String, Object>> done = new AtomicReference<>();
        when(pythonAiProxyService.streamRag(any(), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            BiConsumer<String, Object> consumer = invocation.getArgument(2);
            consumer.accept("generation_start", validGeneratedResponse());
            Map<String, Object> changed = validGeneratedResponse();
            resource(changed).put("id", "res_changed");
            evidenceChain(changed).put("resourceLinks", List.of(Map.of(
                    "resourceId", "res_changed", "evidenceIds", List.of())));
            refreshChainIntegrity(evidenceChain(changed));
            done.set(changed);
            consumer.accept("done", changed);
            return new SseEmitter();
        });

        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());

        JsonNode donePayload = objectMapper.valueToTree(done.get());
        assertThat(savedExport.get().getResourceId()).isEqualTo("res_doc");
        assertThat(donePayload.path("resources").path(0).path("url").asText()).isEmpty();
        assertThat(donePayload.path("resources").path(0).path("availability").asText())
                .isEqualTo("unavailable");
        verify(exportRepository, times(1)).save(any());
    }

    @Test
    void malformedSiblingJsonForcesMalformedEvidenceState() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("attachments", List.of());
        raw.put("resources", List.of());
        Map<String, Object> chain = evidenceChain(raw);
        chain.put("status", "model_only");
        chain.put("sources", List.of());
        chain.put("resourceLinks", List.of());
        refreshChainIntegrity(chain);
        AiLeaderMessage message = new AiLeaderMessage();
        message.setId(79L);
        message.setLeaderSessionId(9L);
        message.setRole(AiLeaderMessage.ROLE_ASSISTANT);
        message.setContent("正文保留");
        message.setMatchedResultsJson("[]");
        message.setResourcesJson("{malformed");
        message.setAttachmentsJson("[]");
        message.setEvidenceChainJson(objectMapper.writeValueAsString(chain));
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(message));

        JsonNode history = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());

        assertThat(history.path("messages").path(0).path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("malformed");
        assertThat(history.path("messages").path(0).path("content").asText()).isEqualTo("正文保留");
    }

    @Test
    void evidenceSourcesAreTrimmedRatherThanDiscardingTheEnvelope() throws Exception {
        Map<String, Object> raw = groundedResponse(21, 801);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        JsonNode response = objectMapper.valueToTree(
                controller.query(request(), authenticatedRequest()).getData());

        assertThat(response.path("resources")).hasSize(1);
        assertThat(response.path("evidenceChain").path("evidenceState").asText()).isEqualTo("available");
        assertThat(response.path("evidenceChain").path("sources")).hasSize(20);
        assertThat(response.path("evidenceChain").path("sources").path(0).path("excerpt").asText()).hasSize(800);
        assertThat(response.path("evidenceChain").path("truncated").asBoolean()).isTrue();
        assertThat(response.path("resources").path(0).path("evidenceIds")).hasSize(20);
    }

    @Test
    void oversizedEnvelopeIsDeterministicallyTrimmedBelowTheByteLimit() throws Exception {
        Map<String, Object> raw = oversizedContentResponse(30);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        JsonNode response = objectMapper.valueToTree(result.getData());
        int envelopeBytes = objectMapper.writeValueAsBytes(Map.of(
                "resources", result.getData().getResources(),
                "evidenceChain", result.getData().getEvidenceChain())).length;

        assertThat(response.path("resources").size()).isBetween(1, 30);
        assertThat(response.path("evidenceChain").path("evidenceState").asText()).isEqualTo("available");
        assertThat(response.path("evidenceChain").path("truncated").asBoolean()).isTrue();
        assertThat(envelopeBytes).isLessThanOrEqualTo(256 * 1024);
    }

    @Test
    void answerDigestMustBindLiveAndHistoricalMessageContent() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        evidenceChain(raw).put("answerDigest", sha256Text("另一份回答"));
        refreshChainIntegrity(evidenceChain(raw));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        JsonNode live = objectMapper.valueToTree(controller.query(request(), authenticatedRequest()).getData());
        assertThat(live.path("evidenceChain").path("evidenceState").asText()).isEqualTo("integrity_failed");

        Map<String, Object> valid = validGeneratedResponse();
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(valid);
        controller.query(request(), authenticatedRequest());
        AiLeaderMessage latest = savedMessages.getLast();
        latest.setContent("被篡改的历史正文");
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(latest));
        JsonNode history = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());
        assertThat(history.path("messages").path(0).path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("integrity_failed");
    }

    @Test
    void nonAvailableEvidenceStateSurvivesPersistenceAndHistoryRestore() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        evidenceChain(raw).put("steps", List.of(Map.of(
                "stage", "tool_result",
                "detail", Map.of("internalCapability", "secret-capability"))));
        refreshChainIntegrity(evidenceChain(raw));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        JsonNode live = objectMapper.valueToTree(controller.query(request(), authenticatedRequest()).getData());
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L))
                .thenReturn(new ArrayList<>(savedMessages));
        JsonNode history = objectMapper.valueToTree(
                controller.sessionDetail("session-1", authenticatedRequest()).getData());
        JsonNode historicalAssistant = history.path("messages").path(history.path("messages").size() - 1);

        assertThat(live.path("evidenceChain").path("evidenceState").asText()).isEqualTo("generation_failed");
        assertThat(historicalAssistant.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("generation_failed");
        assertThat(historicalAssistant.path("content").asText()).isEqualTo("资料已生成");
    }

    @Test
    void canonicalDigestsAndTimesRequireLowercasePrefixedUtcForm() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        Map<String, Object> chain = evidenceChain(raw);
        chain.put("queryDigest", "B".repeat(64));
        chain.put("generatedAt", "2026-07-14T20:00:00+08:00");
        refreshChainIntegrity(chain);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        JsonNode response = objectMapper.valueToTree(
                controller.query(request(), authenticatedRequest()).getData());

        assertThat(response.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("integrity_failed");
    }

    @Test
    void evidenceAndResourceFieldsRejectJsonTypeCoercionAndNonUtcResourceTimes() throws Exception {
        Map<String, Object> typedEvidence = groundedResponse(1, 20);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = new LinkedHashMap<>(
                (Map<String, Object>) ((List<?>) evidenceChain(typedEvidence).get("sources")).getFirst());
        source.put("title", Map.of("internalCapability", "typed-secret"));
        evidenceChain(typedEvidence).put("sources", List.of(source));
        refreshChainIntegrity(evidenceChain(typedEvidence));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(typedEvidence);

        Result<LlmChatResponse> typedResult = controller.query(request(), authenticatedRequest());
        JsonNode typedResponse = objectMapper.valueToTree(typedResult.getData());
        assertThat(typedResponse.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("generation_failed");
        assertThat(objectMapper.writeValueAsString(typedResult.getData())).doesNotContain("typed-secret");

        Map<String, Object> offsetResource = validGeneratedResponse();
        resource(offsetResource).put("createdAt", "2026-07-14T20:00:00+08:00");
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(offsetResource);

        JsonNode offsetResponse = objectMapper.valueToTree(
                controller.query(request(), authenticatedRequest()).getData());
        assertThat(offsetResponse.path("resources")).isEmpty();
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
        chain.put("queryDigest", sha256Text("导出复习资料"));
        chain.put("answerDigest", sha256Text("资料已生成"));
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

    private String sha256Text(String value) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void refreshChainIntegrity(Map<String, Object> chain) throws Exception {
        chain.remove("integrity");
        chain.put("integrity", Map.of(
                "algorithm", "SHA-256",
                "digest", canonicalDigest(chain),
                "scope", "canonical-json-without-integrity",
                "signed", false));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resource(Map<String, Object> response) {
        return (Map<String, Object>) ((List<?>) response.get("resources")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evidenceChain(Map<String, Object> response) {
        return (Map<String, Object>) response.get("evidenceChain");
    }

    private Map<String, Object> groundedResponse(int sourceCount, int excerptLength) throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("attachments", List.of());
        List<Map<String, Object>> sources = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        for (int index = 0; index < sourceCount; index++) {
            String evidenceId = "ev_" + index;
            evidenceIds.add(evidenceId);
            sources.add(Map.of(
                    "evidenceId", evidenceId,
                    "sourceType", "knowledge_base",
                    "sourceId", "source_" + index,
                    "title", "来源 " + index,
                    "excerpt", "证".repeat(excerptLength),
                    "retrievedAt", "2026-07-14T12:00:00Z",
                    "contentDigest", sha256Text("来源 " + index),
                    "accessScope", "request_user"));
        }
        Map<String, Object> resource = resource(raw);
        resource.put("storageKey", "");
        resource.put("kind", "explanation");
        resource.put("deliveryType", "content");
        resource.put("groundingStatus", "grounded");
        resource.put("evidenceIds", evidenceIds);
        resource.put("authScope", "request_user");
        resource.put("integrity", null);
        resource.put("payload", Map.of("type", "content", "content", "资料已生成", "language", "text"));
        resource.put("actions", List.of());
        Map<String, Object> chain = evidenceChain(raw);
        chain.put("status", "grounded");
        chain.put("sources", sources);
        chain.put("resourceLinks", List.of(Map.of("resourceId", "res_doc", "evidenceIds", evidenceIds)));
        refreshChainIntegrity(chain);
        return raw;
    }

    private Map<String, Object> oversizedContentResponse(int count) throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("attachments", List.of());
        List<Map<String, Object>> resources = new ArrayList<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("schemaVersion", "assistant-resource-v1");
            item.put("id", "res_content_" + index);
            item.put("messageId", null);
            item.put("kind", "explanation");
            item.put("deliveryType", "content");
            item.put("groundingStatus", "model_only");
            item.put("title", "内容 " + index);
            item.put("summary", "摘要 ".repeat(80));
            item.put("mimeType", "text/plain");
            item.put("storageKey", "");
            item.put("url", "");
            item.put("previewUrl", "");
            item.put("sourceType", "response_content");
            item.put("sourceId", "assistant_answer");
            item.put("evidenceIds", List.of());
            item.put("actions", List.of());
            item.put("authScope", "request_user");
            item.put("createdAt", "2026-07-14T12:00:00Z");
            item.put("expiresAt", null);
            item.put("integrity", null);
            item.put("payload", Map.of("type", "content", "content", "内".repeat(12_000), "language", "text"));
            item.put("metadata", Map.of());
            resources.add(item);
            links.add(Map.of("resourceId", item.get("id"), "evidenceIds", List.of()));
        }
        raw.put("resources", resources);
        Map<String, Object> chain = evidenceChain(raw);
        chain.put("resourceLinks", links);
        refreshChainIntegrity(chain);
        return raw;
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
