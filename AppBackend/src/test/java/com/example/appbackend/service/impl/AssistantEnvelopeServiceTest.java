package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.repository.AiLeaderGeneratedExportRepository;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AssistantEnvelopeServiceTest {

    private AssistantEnvelopeService service;

    @BeforeEach
    void setUp() {
        service = new AssistantEnvelopeService(
                mock(AiLeaderMessageRepository.class),
                mock(AiLeaderGeneratedExportRepository.class),
                new ObjectMapper().findAndRegisterModules(),
                "cdn.example.edu");
    }

    @Test
    void liveResponseKeepsOnlySafeProfileTimingDiagnostics() {
        LlmChatResponse response = new LlmChatResponse();
        response.setAnswer("测试回答");
        response.setAnswerType("text");
        response.setRetrievalMeta(new LinkedHashMap<>(Map.of(
                "profileMs", 12,
                "profileContextSource", "local_snapshot",
                "firstTokenMs", 14,
                "profileSnapshot", Map.of("score", 99),
                "timings", Map.of("profileMs", 12, "planMs", 1, "firstTokenMs", 14)
        )));

        service.prepareLiveResponse(response, Map.of(), "测试问题");

        assertThat(response.getRetrievalMeta())
                .containsEntry("profileMs", 12)
                .containsEntry("profileContextSource", "local_snapshot")
                .containsEntry("firstTokenMs", 14)
                .doesNotContainKey("profileSnapshot");
        @SuppressWarnings("unchecked")
        Map<String, Object> timings = (Map<String, Object>) response.getRetrievalMeta().get("timings");
        assertThat(timings)
                .containsEntry("profileMs", 12)
                .containsEntry("planMs", 1)
                .containsEntry("firstTokenMs", 14);
    }

    @Test
    void learningProgressUsesClosedScalarAllowlistWithoutChangingCampusContract() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", "wf-1");
        payload.put("stage", "planning");
        payload.put("progress", 35);
        payload.put("message", "正在规划");
        payload.put("retryable", false);
        payload.put("userId", 42L);
        payload.put("profile", Map.of("score", 99));
        payload.put("token", "internal");

        service.sanitizeLearningSseEventPayload("planning", payload, Set.of("internal"));

        assertThat(payload).containsExactlyInAnyOrderEntriesOf(Map.of(
                "workflowId", "wf-1",
                "stage", "planning",
                "progress", 35,
                "message", "正在规划",
                "retryable", false
        ));

        Map<String, Object> campus = new LinkedHashMap<>();
        campus.put("message", "校园查询完成");
        campus.put("workflowId", "must-not-enter-campus-contract");
        service.sanitizeSseEventPayload("done", campus, Set.of());
        assertThat(campus).containsExactly(Map.entry("message", "校园查询完成"));
    }

    @Test
    void agentDoneKeepsOnlyAValidatedAssistantResourceAndSafeLearningMetadata() {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("schemaVersion", "assistant-resource-v1");
        resource.put("id", "resource-note-1");
        resource.put("kind", "explanation");
        resource.put("deliveryType", "content");
        resource.put("groundingStatus", "grounded");
        resource.put("title", "列表切片讲义");
        resource.put("summary", "理解 start、stop 与 step");
        resource.put("payload", Map.of("type", "content", "content", "切片讲义正文"));
        resource.put("evidenceIds", List.of("ref-list-slicing"));
        resource.put("actions", List.of());
        resource.put("authScope", "session_owner");
        resource.put("createdAt", "2026-07-15T10:00:00Z");
        resource.put("availability", "active");
        resource.put("metadata", Map.of(
                "courseKey", "python",
                "knowledgePoint", "列表切片",
                "learningPathId", "71",
                "learningPathItemKey", "python-slicing-1",
                "resourceKind", "knowledge_note",
                "resourceType", "knowledge_note",
                "reviewStatus", "passed",
                "profile", "must-not-leak"
        ));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", "wf-1");
        payload.put("stage", "generation");
        payload.put("progress", 45);
        payload.put("agentName", "python_knowledge_note_agent");
        payload.put("resourceType", "knowledge_note");
        payload.put("resource", resource);
        payload.put("raw", Map.of("apiKey", "must-not-leak"));

        service.sanitizeLearningSseEventPayload("agent_done", payload, Set.of());

        assertThat(payload).containsKeys(
                "workflowId", "stage", "progress", "agentName", "resourceType", "resource");
        assertThat(payload).doesNotContainKeys("raw", "userId", "profile", "token");
        @SuppressWarnings("unchecked")
        Map<String, Object> safeResource = (Map<String, Object>) payload.get("resource");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) safeResource.get("metadata");
        assertThat(metadata).containsEntry("courseKey", "python")
                .containsEntry("resourceType", "knowledge_note")
                .containsEntry("reviewStatus", "passed")
                .doesNotContainKey("profile");
    }

    @Test
    void resourceCarryingAnInternalCapabilityIsDroppedFailClosed() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowId", "wf-1");
        payload.put("stage", "generation");
        payload.put("progress", 45);
        payload.put("resourceType", "code_lab");
        payload.put("resource", Map.of(
                "schemaVersion", "assistant-resource-v1",
                "id", "resource-code-1",
                "kind", "code_example",
                "internalCapability", "private-capability"
        ));

        service.sanitizeLearningSseEventPayload(
                "agent_done", payload, Set.of("private-capability"));

        assertThat(payload).doesNotContainKey("resource");
        assertThat(payload).doesNotContainValue("private-capability");
    }

    @Test
    void learningPathTextCannotPersistCapabilitiesOrInternalUrls() {
        assertThat(service.sanitizeLearningText(
                "切片路径 private-capability", 200, "安全路径",
                Set.of("private-capability")))
                .isEqualTo("切片路径");
        assertThat(service.sanitizeLearningText(
                "http://127.0.0.1:8081/internal/export", 200, "安全路径", Set.of()))
                .isEqualTo("安全路径");
    }
}
