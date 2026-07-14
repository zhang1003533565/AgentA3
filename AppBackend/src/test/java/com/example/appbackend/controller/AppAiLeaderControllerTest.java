package com.example.appbackend.controller;

import com.example.appbackend.dto.LlmChatRequest;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.AiLeaderGeneratedExport;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.repository.AiLeaderGeneratedExportRepository;
import com.example.appbackend.service.UserProfileService;
import com.example.appbackend.service.impl.AssistantEnvelopeService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
                exportRepository,
                userProfileService,
                objectMapper,
                assistantEnvelopeService
        );
    }

    @Test
    void downloadExportRejectsForeignSessionWithoutProxy() {
        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.downloadExport("foreign-session", 701L, "export.bin", authenticatedRequest())
        );

        assertThat(error.getCode()).isEqualTo(404);
        verify(pythonAiProxyService, never()).downloadGeneratedExport(any(), any());
    }

    @Test
    void downloadExportRejectsForeignMessageWithoutProxy() {
        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.downloadExport("session-1", 702L, "export.bin", authenticatedRequest())
        );

        assertThat(error.getCode()).isEqualTo(404);
        verify(exportRepository).findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
                42L, 9L, 702L, "export.bin", AiLeaderGeneratedExport.STATUS_ACTIVE);
        verify(pythonAiProxyService, never()).downloadGeneratedExport(any(), any());
    }

    @Test
    void downloadExportRejectsUnboundStorageKeyWithoutProxy() {
        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.downloadExport("session-1", 703L, "unbound.bin", authenticatedRequest())
        );

        assertThat(error.getCode()).isEqualTo(404);
        verify(exportRepository).findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
                42L, 9L, 703L, "unbound.bin", AiLeaderGeneratedExport.STATUS_ACTIVE);
        verify(pythonAiProxyService, never()).downloadGeneratedExport(any(), any());
    }

    @Test
    void downloadExportQueriesOnlyActiveOwnerBoundManifest() throws Exception {
        byte[] bytes = "verified export".getBytes(StandardCharsets.UTF_8);
        AiLeaderGeneratedExport manifest = downloadManifest(704L, "bound.bin", bytes);
        stubDownload(manifest, bytes, MediaType.APPLICATION_OCTET_STREAM);

        controller.downloadExport("session-1", 704L, "bound.bin", authenticatedRequest());

        verify(exportRepository).findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
                42L, 9L, 704L, "bound.bin", AiLeaderGeneratedExport.STATUS_ACTIVE);
    }

    @Test
    void downloadExportUsesPersistedCapabilityAndManifestContentType() throws Exception {
        byte[] bytes = new byte[]{0, 1, -1, 2, 3};
        AiLeaderGeneratedExport manifest = downloadManifest(705L, "verified.bin", bytes);
        manifest.setMimeType("text/plain");
        stubDownload(manifest, bytes, MediaType.APPLICATION_PDF);

        ResponseEntity<byte[]> response = controller.downloadExport(
                "session-1", 705L, "verified.bin", authenticatedRequest());

        assertThat(response.getBody()).containsExactly(bytes);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(bytes.length);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("private, no-store");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        verify(pythonAiProxyService).downloadGeneratedExport("verified.bin", "persisted-capability");
        verify(pythonAiProxyService, never()).downloadGeneratedExport(any(), eq("Bearer test-token"));
    }

    @Test
    void downloadExportSanitizesManifestFilenameDisposition() throws Exception {
        byte[] bytes = "safe".getBytes(StandardCharsets.UTF_8);
        AiLeaderGeneratedExport manifest = downloadManifest(706L, "safe.bin", bytes);
        manifest.setFileName("../复习\\资料\"\r\nX-Injected: yes.docx");
        stubDownload(manifest, bytes, MediaType.APPLICATION_OCTET_STREAM);

        ResponseEntity<byte[]> response = controller.downloadExport(
                "session-1", 706L, "safe.bin", authenticatedRequest());

        String header = response.getHeaders().getFirst("Content-Disposition");
        assertThat(header).isNotBlank().doesNotContain("\r", "\n", "../", "\\");
        String filename = ContentDisposition.parse(header).getFilename();
        assertThat(filename).contains("复习").doesNotContain("/", "\\", "\r", "\n", ":", "\"");
    }

    @Test
    void downloadExportReturnsGoneForExpiredManifestWithoutProxy() throws Exception {
        byte[] bytes = "expired".getBytes(StandardCharsets.UTF_8);
        AiLeaderGeneratedExport manifest = downloadManifest(707L, "expired.bin", bytes);
        manifest.setExpiresAt(Instant.now().minusSeconds(1));
        when(exportRepository.findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
                42L, 9L, 707L, "expired.bin", AiLeaderGeneratedExport.STATUS_ACTIVE))
                .thenReturn(Optional.of(manifest));

        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.downloadExport("session-1", 707L, "expired.bin", authenticatedRequest())
        );

        assertThat(error.getCode()).isEqualTo(410);
        verify(pythonAiProxyService, never()).downloadGeneratedExport(any(), any());
    }

    @Test
    void downloadExportRejectsSizeMismatchBeforeReturningBody() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        AiLeaderGeneratedExport manifest = downloadManifest(708L, "size.bin", expected);
        byte[] actual = "short".getBytes(StandardCharsets.UTF_8);
        stubDownload(manifest, actual, MediaType.APPLICATION_OCTET_STREAM);

        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.downloadExport("session-1", 708L, "size.bin", authenticatedRequest())
        );

        assertThat(error.getCode()).isEqualTo(409);
    }

    @Test
    void downloadExportRejectsDigestMismatchBeforeReturningBody() throws Exception {
        byte[] expected = "content-a".getBytes(StandardCharsets.UTF_8);
        AiLeaderGeneratedExport manifest = downloadManifest(709L, "digest.bin", expected);
        byte[] actual = "content-b".getBytes(StandardCharsets.UTF_8);
        stubDownload(manifest, actual, MediaType.APPLICATION_OCTET_STREAM);

        BusinessException error = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.downloadExport("session-1", 709L, "digest.bin", authenticatedRequest())
        );

        assertThat(error.getCode()).isEqualTo(409);
    }

    @Test
    void downloadExportFallsBackToSafeMimeType() throws Exception {
        byte[] bytes = "mime".getBytes(StandardCharsets.UTF_8);
        AiLeaderGeneratedExport manifest = downloadManifest(710L, "mime.bin", bytes);
        manifest.setMimeType("not a valid mime");
        stubDownload(manifest, bytes, null);

        ResponseEntity<byte[]> response = controller.downloadExport(
                "session-1", 710L, "mime.bin", authenticatedRequest());

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
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
            PythonAiProxyService.SseEventHandler consumer = invocation.getArgument(2);
            Map<String, Object> first = validGeneratedResponse();
            Map<String, Object> last = validGeneratedResponse();
            first.put("rawDebug", Map.of("internalCapability", "secret-capability"));
            last.put("rawDebug", Map.of("endpoint", "http://localhost:8081/internal"));
            generationStart.set(first);
            done.set(last);
            consumer.handle("generation_start", first);
            consumer.handle("done", last);
            return new SseEmitter();
        });

        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());

        assertThat(generationStart.get().get("messageId")).isEqualTo(101L);
        assertThat(done.get().get("messageId")).isEqualTo(101L);
        assertThat(objectMapper.writeValueAsString(done.get()))
                .contains("/sessions/session-1/messages/101/exports/")
                .doesNotContain("secret-capability", "internalCapability", "localhost");
        assertThat(generationStart.get()).doesNotContainKey("rawDebug");
        assertThat(done.get()).doesNotContainKey("rawDebug");
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
    void resourceTitlesUseTheSharedCanonicalPersistenceContract() throws Exception {
        Map<String, Object> unsafe = validGeneratedResponse();
        resource(unsafe).put("title", "第一行\n第二行");
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(unsafe);

        JsonNode rejected = objectMapper.valueToTree(
                controller.query(request(), authenticatedRequest()).getData());

        assertThat(rejected.path("resources")).isEmpty();
        assertThat(rejected.path("evidenceChain").path("evidenceState").asText())
                .isEqualTo("generation_failed");

        String maxTitle = "资".repeat(240);
        Map<String, Object> canonical = validGeneratedResponse();
        resource(canonical).put("title", maxTitle);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(canonical);

        JsonNode accepted = objectMapper.valueToTree(
                controller.query(request(), authenticatedRequest()).getData());

        assertThat(accepted.path("resources")).hasSize(1);
        assertThat(accepted.path("resources").path(0).path("title").asText())
                .isEqualTo(maxTitle);
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
            PythonAiProxyService.SseEventHandler consumer = invocation.getArgument(2);
            consumer.handle("generation_start", validGeneratedResponse());
            consumer.handle("generation_start", validGeneratedResponse());
            consumer.handle("done", validGeneratedResponse());
            consumer.handle("done", validGeneratedResponse());
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
            PythonAiProxyService.SseEventHandler consumer = invocation.getArgument(2);
            consumer.handle("generation_start", validGeneratedResponse());
            Map<String, Object> changed = validGeneratedResponse();
            resource(changed).put("id", "res_changed");
            evidenceChain(changed).put("resourceLinks", List.of(Map.of(
                    "resourceId", "res_changed", "evidenceIds", List.of())));
            refreshChainIntegrity(evidenceChain(changed));
            done.set(changed);
            consumer.handle("done", changed);
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
                .isEqualTo("generation_failed");
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

    @Test
    void evidencePublicStringsRejectInternalUrlsForbiddenTermsAndActualCapabilities() throws Exception {
        Map<String, Object> sourceLeak = groundedResponse(1, 20);
        Map<String, Object> source = new LinkedHashMap<>(evidenceSources(sourceLeak).getFirst());
        source.put("title", "secret-capability");
        source.put("excerpt", "http://localhost:8081/internal/exports");
        evidenceChain(sourceLeak).put("sources", List.of(source));
        sourceLeak.put("attachments", validGeneratedResponse().get("attachments"));
        refreshChainIntegrity(evidenceChain(sourceLeak));

        Map<String, Object> generationLeak = validGeneratedResponse();
        Map<String, Object> generation = new LinkedHashMap<>(generation(generationLeak));
        generation.put("model", "secret-capability");
        generation.put("agent", "http://localhost:8081/internal/leader");
        evidenceChain(generationLeak).put("generation", generation);
        refreshChainIntegrity(evidenceChain(generationLeak));

        Map<String, Object> stepLeak = validGeneratedResponse();
        evidenceChain(stepLeak).put("steps", List.of(Map.of(
                "stage", "authorization",
                "detail", Map.of("routeReason", "http://localhost:8081/internal?token=hidden"))));
        refreshChainIntegrity(evidenceChain(stepLeak));

        for (Map<String, Object> raw : List.of(sourceLeak, generationLeak, stepLeak)) {
            when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

            Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
            String serialized = objectMapper.writeValueAsString(result.getData());
            AiLeaderMessage assistant = savedMessages.getLast();

            assertThat(result.getData().getEvidenceChain().getEvidenceState()).isEqualTo("generation_failed");
            assertThat(serialized).doesNotContain(
                    "secret-capability", "localhost", "/internal/", "token=hidden", "authorization");
            assertThat(assistant.getEvidenceChainJson()).doesNotContain(
                    "secret-capability", "localhost", "/internal/", "token=hidden", "authorization");
        }
    }

    @Test
    void actualAttachmentCapabilityCannotLeakThroughAnswerOrResourceFields() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("answer", "资料已生成 secret-capability");
        resource(raw).put("title", "secret-capability");
        evidenceChain(raw).put("answerDigest", sha256Text(String.valueOf(raw.get("answer"))));
        refreshChainIntegrity(evidenceChain(raw));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        AiLeaderMessage assistant = savedMessages.getLast();

        assertThat(objectMapper.writeValueAsString(result.getData())).doesNotContain("secret-capability");
        assertThat(assistant.getContent()).doesNotContain("secret-capability");
        assertThat(assistant.getResourcesJson()).doesNotContain("secret-capability");
        assertThat(assistant.getEvidenceChainJson()).doesNotContain("secret-capability");
    }

    @Test
    void syncTopLevelTextFieldsRejectInternalUrlsBeforeResponseAndPersistence() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        raw.put("answer", "http://localhost:8081/internal/exports/private.docx");
        raw.put("searchKeyword", "http://127.0.0.1:8081/generated/private.docx");
        raw.put("outputTypes", List.of("text", "http://192.168.1.10/internal"));
        evidenceChain(raw).put("answerDigest", sha256Text(String.valueOf(raw.get("answer"))));
        refreshChainIntegrity(evidenceChain(raw));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        AiLeaderMessage assistant = savedMessages.getLast();
        String publicResponse = objectMapper.writeValueAsString(result.getData());

        assertThat(publicResponse).doesNotContain("localhost", "127.0.0.1", "192.168.1.10", "/internal/", "/generated/");
        assertThat(assistant.getContent()).doesNotContain("localhost", "/internal/");
        assertThat(assistant.getSearchKeyword()).doesNotContain("127.0.0.1", "/generated/");
        assertThat(assistant.getOutputTypesJson()).doesNotContain("192.168.1.10", "/internal/");
    }

    @Test
    void statusBeforeGenerationStartDiscoversRootCapabilityBeforeAllowlistSanitization() throws Exception {
        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        Map<String, Object> earlyStatus = new LinkedHashMap<>();
        earlyStatus.put("internalCapability", "opaque-A7-value");
        earlyStatus.put("message", "opaque-A7-value");
        earlyStatus.put("status", "running");

        handlerRef.get().handle("status", earlyStatus);

        assertThat(earlyStatus).containsExactlyEntriesOf(Map.of("status", "running"));
        assertThat(objectMapper.valueToTree(earlyStatus).toString()).doesNotContain("opaque-A7-value");

        Map<String, Object> oversizedCapability = new LinkedHashMap<>();
        oversizedCapability.put("internalCapability", "x".repeat(2_049));
        oversizedCapability.put("message", "still running");
        assertThat(handlerRef.get().handle("status", oversizedCapability)).isFalse();
        assertThat(handlerRef.get().handle("done", validGeneratedResponse())).isFalse();
        assertThat(savedMessages.stream().filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())))
                .isEmpty();
    }

    @Test
    void oversizedCapabilityFailsClosedForSynchronousEnvelope() throws Exception {
        Map<String, Object> raw = validGeneratedResponse();
        String oversized = "opaque".repeat(400);
        @SuppressWarnings("unchecked")
        Map<String, Object> attachment = new LinkedHashMap<>(
                (Map<String, Object>) ((List<?>) raw.get("attachments")).getFirst());
        attachment.put("internalCapability", oversized);
        raw.put("attachments", List.of(attachment));
        raw.put("answer", oversized);
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
        AiLeaderMessage assistant = savedMessages.getLast();

        assertThat(objectMapper.writeValueAsString(result.getData())).doesNotContain(oversized);
        assertThat(assistant.getContent()).doesNotContain(oversized);
        assertThat(result.getData().getResources()).isEmpty();
        assertThat(result.getData().getEvidenceChain().getEvidenceState()).isEqualTo("generation_failed");
    }

    @Test
    void streamCarriesEarlyRootAndNestedCapabilitiesIntoDoneEnvelopeAndPersistence() throws Exception {
        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        PythonAiProxyService.SseEventHandler handler = handlerRef.get();
        Map<String, Object> earlyStatus = new LinkedHashMap<>();
        earlyStatus.put("internalCapability", "opaque-A7-value");
        earlyStatus.put("metadata", Map.of("internalCapability", "nested-B9-value"));
        earlyStatus.put("status", "running");
        assertThat(handler.handle("status", earlyStatus)).isTrue();

        Map<String, Object> done = validGeneratedResponse();
        done.put("attachments", List.of());
        done.put("answer", "opaque-A7-value nested-B9-value");
        evidenceChain(done).put("answerDigest", sha256Text(String.valueOf(done.get("answer"))));
        refreshChainIntegrity(evidenceChain(done));

        assertThat(handler.handle("done", done)).isTrue();

        AiLeaderMessage assistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())).findFirst().orElseThrow();
        assertThat(objectMapper.writeValueAsString(done))
                .doesNotContain("opaque-A7-value", "nested-B9-value", "internalCapability");
        assertThat(assistant.getContent()).doesNotContain("opaque-A7-value", "nested-B9-value");
        assertThat(assistant.getEvidenceChainJson()).doesNotContain("opaque-A7-value", "nested-B9-value");
    }

    @Test
    void allInternalRouteAndReservedAddressFormsAreRejectedFromPublicText() throws Exception {
        for (String unsafe : List.of(
                "/internal/rag/exports/key",
                "safe-prefix".repeat(120) + "/internal/rag/exports/key",
                "http://127.0.0.2/private",
                "http://169.254.169.254/latest/meta-data",
                "http://0.0.0.0/private",
                "http://python.internal/private",
                "http://printer.local/private",
                "http://[::1]/private",
                "http://[0:0:0:0:0:0:0:1]/private",
                "http://[::ffff:127.0.0.1]/private",
                "http://[::ffff:7f00:1]/private",
                "http://[fe80::1]/private",
                "http://[fc00::1]/private",
                "http://[fd00::1]/private")) {
            Map<String, Object> raw = validGeneratedResponse();
            raw.put("answer", unsafe);
            raw.put("searchKeyword", unsafe);
            raw.put("outputTypes", List.of("text", unsafe));
            evidenceChain(raw).put("answerDigest", sha256Text(unsafe));
            refreshChainIntegrity(evidenceChain(raw));
            when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

            Result<LlmChatResponse> result = controller.query(request(), authenticatedRequest());
            AiLeaderMessage assistant = savedMessages.getLast();

            assertThat(objectMapper.writeValueAsString(result.getData()))
                    .as("unsafe internal form must not be public: %s", unsafe)
                    .doesNotContain(unsafe);
            assertThat(result.getData().getAnswer()).isEqualTo("内容暂不可用。");
            assertThat(result.getData().getSearchKeyword()).isEmpty();
            assertThat(result.getData().getOutputTypes()).containsExactly("text");
            assertThat(assistant.getContent()).isEqualTo("内容暂不可用。");
            assertThat(assistant.getSearchKeyword()).isEmpty();
            assertThat(objectMapper.readValue(assistant.getOutputTypesJson(), List.class))
                    .containsExactly("text");
        }
    }

    @Test
    void internalAddressClassifierAlsoProtectsEvidenceSseAndDonePayloads() throws Exception {
        Map<String, Object> raw = groundedResponse(1, 20);
        Map<String, Object> source = new LinkedHashMap<>(evidenceSources(raw).getFirst());
        source.put("title", "/internal/rag/source");
        evidenceChain(raw).put("sources", List.of(source));
        Map<String, Object> generation = new LinkedHashMap<>(generation(raw));
        generation.put("model", "http://169.254.169.254/model");
        evidenceChain(raw).put("generation", generation);
        evidenceChain(raw).put("steps", List.of(Map.of(
                "stage", "generation",
                "detail", Map.of("routeReason", "http://[::1]/internal-step"))));
        refreshChainIntegrity(evidenceChain(raw));
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(raw);

        Result<LlmChatResponse> sync = controller.query(request(), authenticatedRequest());
        assertThat(sync.getData().getEvidenceChain().getEvidenceState()).isEqualTo("generation_failed");
        assertThat(objectMapper.writeValueAsString(sync.getData()))
                .doesNotContain("/internal/rag/source", "169.254.169.254", "[::1]");

        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        Map<String, Object> status = new LinkedHashMap<>(Map.of(
                "status", "running", "message", "http://service.internal/progress"));
        assertThat(handlerRef.get().handle("status", status)).isTrue();
        assertThat(status).containsExactlyEntriesOf(Map.of("status", "running"));

        Map<String, Object> done = validGeneratedResponse();
        done.put("answer", "/internal/rag/final");
        evidenceChain(done).put("answerDigest", sha256Text(String.valueOf(done.get("answer"))));
        refreshChainIntegrity(evidenceChain(done));
        assertThat(handlerRef.get().handle("done", done)).isTrue();
        assertThat(objectMapper.writeValueAsString(done)).doesNotContain("/internal/rag/final");
        assertThat(savedMessages.getLast().getContent()).doesNotContain("/internal/rag/final");
    }

    @Test
    void historyTypedEvidenceFailureIsMalformedAndLogsFieldIdentity() throws Exception {
        when(pythonAiProxyService.queryRag(any(), any())).thenReturn(validGeneratedResponse());
        controller.query(request(), authenticatedRequest());
        AiLeaderMessage assistant = savedMessages.getLast();
        ObjectNode chain = (ObjectNode) objectMapper.readTree(assistant.getEvidenceChainJson());
        ((ObjectNode) chain.path("generation")).put("profileContextUsed", "false");
        chain.remove("integrity");
        chain.set("integrity", objectMapper.valueToTree(Map.of(
                "algorithm", "SHA-256",
                "digest", canonicalDigest(objectMapper.convertValue(chain, Map.class)),
                "scope", "canonical-json-without-integrity",
                "signed", false)));
        assistant.setEvidenceChainJson(objectMapper.writeValueAsString(chain));
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(9L)).thenReturn(List.of(assistant));

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(AssistantEnvelopeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            JsonNode history = objectMapper.valueToTree(
                    controller.sessionDetail("session-1", authenticatedRequest()).getData());

            assertThat(history.path("messages").path(0).path("evidenceChain").path("evidenceState").asText())
                    .isEqualTo("malformed");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("messageId=101")
                            && message.contains("field=evidenceChain")
                            && message.contains("errorType=typedValidation"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void streamReplacesTerminalPayloadsAndSanitizesNonTerminalEvents() throws Exception {
        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        PythonAiProxyService.SseEventHandler handler = handlerRef.get();

        Map<String, Object> start = validGeneratedResponse();
        start.put("rogue", "secret-capability");
        handler.handle("generation_start", start);
        assertThat(start).doesNotContainKey("rogue");
        assertThat(objectMapper.writeValueAsString(start))
                .doesNotContain("secret-capability", "internalCapability", "localhost");

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "running");
        status.put("message", "secret-capability");
        status.put("internalCapability", "secret-capability");
        status.put("endpoint", "http://localhost:8081/internal");
        status.put("unexpected", Map.of("token", "hidden"));
        handler.handle("status", status);

        assertThat(status.keySet()).containsOnly("status");
        assertThat(objectMapper.writeValueAsString(status))
                .doesNotContain("secret-capability", "internalCapability", "localhost", "unexpected", "hidden");

        Map<String, Map<String, Object>> eventPayloads = new LinkedHashMap<>();
        eventPayloads.put("tool_start", new LinkedHashMap<>(Map.of(
                "toolName", "document_export", "message", "正在生成", "rogue", "secret-capability")));
        eventPayloads.put("session", new LinkedHashMap<>(Map.of(
                "sessionId", "session-1", "status", "active", "rogue", "secret-capability")));
        eventPayloads.put("search", new LinkedHashMap<>(Map.of(
                "query", "课程资料", "resultCount", 2, "rogue", "secret-capability")));
        eventPayloads.put("delta", new LinkedHashMap<>(Map.of(
                "delta", "安全增量", "index", 1, "rogue", "secret-capability")));
        eventPayloads.put("custom_event", new LinkedHashMap<>(Map.of(
                "message", "安全摘要", "stage", "custom", "rogue", "secret-capability")));
        for (Map.Entry<String, Map<String, Object>> event : eventPayloads.entrySet()) {
            assertThat(handler.handle(event.getKey(), event.getValue())).isTrue();
            assertThat(event.getValue()).doesNotContainKey("rogue");
            assertThat(objectMapper.writeValueAsString(event.getValue())).doesNotContain("secret-capability");
        }
    }

    @Test
    void streamStartThenErrorUsesOneSafeTerminalEnvelopeForLiveAndHistory() throws Exception {
        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        PythonAiProxyService.SseEventHandler handler = handlerRef.get();
        Map<String, Object> start = validGeneratedResponse();
        handler.handle("generation_start", start);
        Map<String, Object> error = new LinkedHashMap<>(Map.of(
                "message", "python raw secret-capability",
                "internalCapability", "secret-capability",
                "endpoint", "http://localhost:8081/internal"));

        handler.handle("error", error);

        AiLeaderMessage assistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())).findFirst().orElseThrow();
        JsonNode live = objectMapper.valueToTree(error);
        JsonNode persistedEvidence = objectMapper.readTree(assistant.getEvidenceChainJson());
        assertThat(live.path("messageId").asLong()).isEqualTo(start.get("messageId"));
        assertThat(live.path("answer").asText()).isEqualTo(assistant.getContent());
        assertThat(live.path("resources")).hasSize(0);
        assertThat(live.path("evidenceChain").path("evidenceState").asText()).isEqualTo("generation_failed");
        assertThat(live.path("evidenceChain")).isEqualTo(persistedEvidence);
        assertThat(live.toString()).doesNotContain("python raw", "secret-capability", "localhost", "internalCapability");
        verify(userProfileService, never()).addEvidence(anyLong(), any());
    }

    @Test
    void nonObjectStartErrorAndDonePayloadsAreSuppressedWithoutChangingLiveHistoryState() throws Exception {
        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        PythonAiProxyService.SseEventHandler handler = handlerRef.get();

        assertThat(handler.handle("generation_start", 42)).isFalse();
        assertThat(savedMessages.stream().filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())))
                .isEmpty();

        Map<String, Object> start = validGeneratedResponse();
        assertThat(handler.handle("generation_start", start)).isTrue();
        AiLeaderMessage assistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())).findFirst().orElseThrow();
        String startContent = assistant.getContent();
        String startEvidence = assistant.getEvidenceChainJson();

        assertThat(handler.handle("error", "secret-capability")).isFalse();
        assertThat(handler.handle("done", List.of("secret-capability"))).isFalse();

        assertThat(assistant.getContent()).isEqualTo(startContent);
        assertThat(assistant.getEvidenceChainJson()).isEqualTo(startEvidence);
        verify(userProfileService, never()).addEvidence(anyLong(), any());
    }

    @Test
    void streamTerminalStateSuppressesDoneErrorAndErrorDoneSecondSideEffects() throws Exception {
        AtomicReference<PythonAiProxyService.SseEventHandler> firstHandlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        PythonAiProxyService.SseEventHandler firstHandler = firstHandlerRef.get();
        firstHandler.handle("generation_start", validGeneratedResponse());
        firstHandler.handle("done", validGeneratedResponse());
        AiLeaderMessage firstAssistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())).findFirst().orElseThrow();
        String completedJson = firstAssistant.getEvidenceChainJson();
        assertThat(firstHandler.handle("error", new LinkedHashMap<>(Map.of("message", "late raw failure"))))
                .isFalse();

        assertThat(firstAssistant.getContent()).isEqualTo("资料已生成");
        assertThat(firstAssistant.getEvidenceChainJson()).isEqualTo(completedJson);
        verify(userProfileService, times(1)).addEvidence(anyLong(), any());

        savedMessages.clear();
        AtomicReference<PythonAiProxyService.SseEventHandler> secondHandlerRef = captureStreamHandler();
        controller.queryStream(request(), "Bearer test-token", authenticatedRequest());
        PythonAiProxyService.SseEventHandler secondHandler = secondHandlerRef.get();
        secondHandler.handle("generation_start", validGeneratedResponse());
        Map<String, Object> safeError = new LinkedHashMap<>(Map.of("message", "first raw failure"));
        secondHandler.handle("error", safeError);
        AiLeaderMessage secondAssistant = savedMessages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole())).findFirst().orElseThrow();
        String failureAnswer = secondAssistant.getContent();
        String failureEvidence = secondAssistant.getEvidenceChainJson();
        assertThat(secondHandler.handle("done", validGeneratedResponse())).isFalse();

        assertThat(secondAssistant.getContent()).isEqualTo(failureAnswer);
        assertThat(secondAssistant.getEvidenceChainJson()).isEqualTo(failureEvidence);
        verify(userProfileService, times(1)).addEvidence(anyLong(), any());
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<PythonAiProxyService.SseEventHandler> captureStreamHandler() {
        AtomicReference<PythonAiProxyService.SseEventHandler> handlerRef = new AtomicReference<>();
        when(pythonAiProxyService.streamRag(any(), any(), any())).thenAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(2));
            return new SseEmitter();
        });
        return handlerRef;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> evidenceSources(Map<String, Object> response) {
        return (List<Map<String, Object>>) evidenceChain(response).get("sources");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> generation(Map<String, Object> response) {
        return (Map<String, Object>) evidenceChain(response).get("generation");
    }

    private AiLeaderGeneratedExport downloadManifest(Long messageId, String storageKey, byte[] bytes) throws Exception {
        AiLeaderGeneratedExport manifest = new AiLeaderGeneratedExport();
        manifest.setUserId(42L);
        manifest.setLeaderSessionId(9L);
        manifest.setMessageId(messageId);
        manifest.setResourceId("res-download-" + messageId);
        manifest.setStorageKey(storageKey);
        manifest.setFileName("复习资料.bin");
        manifest.setMimeType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        manifest.setSize((long) bytes.length);
        manifest.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        manifest.setPythonCapability("persisted-capability");
        manifest.setCreatedAt(Instant.now().minusSeconds(60));
        manifest.setExpiresAt(Instant.now().plusSeconds(3600));
        manifest.setStatus(AiLeaderGeneratedExport.STATUS_ACTIVE);
        return manifest;
    }

    private void stubDownload(AiLeaderGeneratedExport manifest, byte[] bytes, MediaType contentType) {
        when(exportRepository.findByUserIdAndLeaderSessionIdAndMessageIdAndStorageKeyAndStatus(
                manifest.getUserId(),
                manifest.getLeaderSessionId(),
                manifest.getMessageId(),
                manifest.getStorageKey(),
                AiLeaderGeneratedExport.STATUS_ACTIVE
        )).thenReturn(Optional.of(manifest));
        when(pythonAiProxyService.downloadGeneratedExport(
                manifest.getStorageKey(), manifest.getPythonCapability()))
                .thenReturn(new PythonAiProxyService.GeneratedExportResponse(bytes, contentType, bytes.length));
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
