package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AssistantResourceDTO;
import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.UserProfileEvidence;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.service.CourseKnowledgeService;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.LearningWorkflowStateStore;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningWorkflowServiceImplTest {

    private PythonAiProxyService proxy;
    private LearningPathService pathService;
    private UserProfileService profileService;
    private CourseKnowledgeService knowledgeService;
    private UserProfileEvidenceRepository evidenceRepository;
    private AiLeaderSessionRepository sessionRepository;
    private AiLeaderMessageRepository messageRepository;
    private AssistantEnvelopeService envelopeService;
    private InMemoryStateStore stateStore;
    private LearningWorkflowServiceImpl service;
    private AtomicReference<Map<String, Object>> pythonRequest;
    private AtomicReference<PythonAiProxyService.SseEventHandler> eventHandler;

    @BeforeEach
    void setUp() {
        proxy = mock(PythonAiProxyService.class);
        pathService = mock(LearningPathService.class);
        profileService = mock(UserProfileService.class);
        knowledgeService = mock(CourseKnowledgeService.class);
        evidenceRepository = mock(UserProfileEvidenceRepository.class);
        sessionRepository = mock(AiLeaderSessionRepository.class);
        messageRepository = mock(AiLeaderMessageRepository.class);
        envelopeService = mock(AssistantEnvelopeService.class);
        stateStore = new InMemoryStateStore();
        pythonRequest = new AtomicReference<>();
        eventHandler = new AtomicReference<>();

        when(profileService.buildLeaderProfileContext(anyLong(), anyString())).thenReturn(Map.of(
                "overallScore", 72,
                "resourcePreference", List.of("code_example")
        ));
        when(profileService.getSnapshot(anyLong(), anyString())).thenReturn(profileSnapshot());
        when(pathService.getHome(42L, "python")).thenReturn(home());
        when(pathService.getActivePath(42L, "python")).thenReturn(home().getActivePath());
        when(knowledgeService.retrieve(any())).thenReturn(retrieval());
        when(proxy.streamLearningWorkflow(anyMap(), anyString(), any())).thenAnswer(invocation -> {
            pythonRequest.set(new LinkedHashMap<>(invocation.getArgument(0)));
            eventHandler.set(invocation.getArgument(2));
            return new SseEmitter();
        });
        AtomicLong sessionIds = new AtomicLong(10L);
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            AiLeaderSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(sessionIds.getAndIncrement());
            return session;
        });
        when(sessionRepository.findById(anyLong())).thenAnswer(invocation -> Optional.of(session(
                invocation.getArgument(0), "learning-wf-test")));
        AtomicLong messageIds = new AtomicLong(100L);
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            AiLeaderMessage message = invocation.getArgument(0);
            if (message.getId() == null) message.setId(messageIds.getAndIncrement());
            return message;
        });
        when(messageRepository.countByLeaderSessionId(anyLong())).thenReturn(2L);
        when(envelopeService.scanInternalCapabilities(any()))
                .thenReturn(new AssistantEnvelopeService.CapabilityScan(java.util.Set.of(), false));
        when(envelopeService.mergeInternalCapabilities(any(), any()))
                .thenAnswer(invocation -> new AssistantEnvelopeService.CapabilityScan(
                        invocation.getArgument(1), false));
        when(envelopeService.prepareLiveResponse(any(), anyMap(), anyString(), any()))
                .thenAnswer(invocation -> {
                    LlmChatResponse response = invocation.getArgument(0);
                    Map<String, Object> payload = invocation.getArgument(1);
                    List<AssistantResourceDTO> resources = ((List<?>) payload.getOrDefault(
                            "resources", List.of())).stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .map(raw -> {
                                AssistantResourceDTO resource = new AssistantResourceDTO();
                                resource.setSchemaVersion(String.valueOf(raw.get("schemaVersion")));
                                resource.setId(String.valueOf(raw.get("id")));
                                resource.setKind(String.valueOf(raw.get("kind")));
                                resource.setTitle(String.valueOf(raw.get("title")));
                                @SuppressWarnings("unchecked")
                                Map<String, Object> metadata = (Map<String, Object>) raw.get("metadata");
                                resource.setMetadata(metadata);
                                return resource;
                            })
                            .toList();
                    response.setResources(resources);
                    return new AssistantEnvelopeService.PreparedEnvelope(List.of(), java.util.Set.of());
                });
        when(envelopeService.persistAssistantMessage(anyLong(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    AiLeaderMessage message = new AiLeaderMessage();
                    message.setId(88L);
                    return message;
                });
        when(pathService.replaceActivePath(anyLong(), any())).thenAnswer(invocation -> {
            LearningPathDTO.PathDraft draft = invocation.getArgument(1);
            LearningPathDTO.PathView view = new LearningPathDTO.PathView();
            view.setId(71L);
            view.setVersion(2);
            view.setCourseKey(draft.getCourseKey());
            return view;
        });

        service = new LearningWorkflowServiceImpl(
                proxy,
                stateStore,
                pathService,
                profileService,
                knowledgeService,
                evidenceRepository,
                sessionRepository,
                messageRepository,
                envelopeService,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void startInjectsOnlyServerOwnedProfileMasteryPathAndReferences() {
        LearningPathDTO.GenerateRequest request = generateRequest();

        service.start(42L, request, "Bearer student-token");

        Map<String, Object> payload = pythonRequest.get();
        assertThat(payload).containsEntry("input", "列表切片")
                .containsEntry("intent", "resource_package")
                .containsEntry("agentName", "leader_agent")
                .doesNotContainKeys("userId", "score", "llmModel");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        assertThat(metadata.keySet()).containsExactlyInAnyOrder(
                "courseKey", "workflowId", "profileSnapshot", "masterySnapshot",
                "pathSnapshot", "references", "requestedResourceTypes");
        assertThat(metadata).containsEntry("courseKey", "python")
                .doesNotContainKeys("userId", "score", "llmModel");
        assertThat(metadata.get("profileSnapshot")).isEqualTo(Map.of(
                "overallScore", 72,
                "resourcePreference", List.of("code_example")
        ));
        assertThat((List<?>) metadata.get("masterySnapshot")).hasSize(1);
        assertThat((List<?>) metadata.get("references")).hasSize(1);
        assertThat(stateStore.only().getView().getStatus()).isEqualTo("accepted");
    }

    @Test
    void rejectsClientControlledCourseIntentAndResourceTypesBeforeCallingPython() {
        for (LearningPathDTO.GenerateRequest request : List.of(
                request("java", "resource_package", List.of("knowledge_note")),
                request("python", "campus_tool", List.of("knowledge_note")),
                request("python", "resource_package", List.of("shell_access")))) {
            assertThrows(BusinessException.class,
                    () -> service.start(42L, request, "Bearer student-token"));
        }

        verify(proxy, never()).streamLearningWorkflow(anyMap(), anyString(), any());
    }

    @Test
    void doneIsConsumedOnceProgressNeverRegressesAndPythonPathIsExplicitlyMapped() {
        service.start(42L, generateRequest(), "Bearer student-token");
        String workflowId = stateStore.only().getWorkflowId();
        PythonAiProxyService.SseEventHandler handler = eventHandler.get();

        assertThat(handler.handle("accepted", event(workflowId, 0))).isTrue();
        assertThat(handler.handle("planning", event(workflowId, 35))).isTrue();
        Map<String, Object> regressed = event(workflowId, 15);
        assertThat(handler.handle("retrieval", regressed)).isTrue();
        assertThat(regressed.get("progress")).isEqualTo(35);

        Map<String, Object> done = event(workflowId, 100);
        done.put("status", "completed");
        done.put("answer", "已生成 Python 列表切片个性化资源包");
        done.put("resources", List.of(
                resource("resource-note-1", "explanation", "列表切片讲义", "knowledge_note"),
                resource("resource-code-1", "code_example", "列表切片实操", "code_lab")
        ));
        done.put("pathDraft", Map.of(
                "title", "列表切片补强路径",
                "goal", "掌握 Python 列表切片",
                "personalizationReasons", List.of("当前掌握度偏低"),
                "items", List.of(Map.of(
                        "order", 1,
                        "title", "切片边界",
                        "goal", "理解 start、stop、step",
                        "evidenceIds", List.of("ref-list-slicing")
                ))
        ));

        assertThat(handler.handle("done", done)).isTrue();
        assertThat(handler.handle("done", done)).isFalse();

        LearningWorkflowStateStore.WorkflowState stored = stateStore.only();
        assertThat(stored.getView().getStatus()).isEqualTo("completed");
        assertThat(stored.getView().getProgress()).isEqualTo(100);
        assertThat(stored.getView().getMessageId()).isEqualTo(88L);
        assertThat(stored.getView().getResources()).containsKey("knowledge_note");
        assertThat(done.keySet()).containsExactlyInAnyOrder(
                "workflowId", "courseKey", "topic", "intent", "status", "stage", "progress",
                "message", "activeAgentName", "activeResourceType", "resources", "errors",
                "path", "messageId", "startedAt", "updatedAt");

        ArgumentCaptor<LearningPathDTO.PathDraft> draftCaptor =
                ArgumentCaptor.forClass(LearningPathDTO.PathDraft.class);
        verify(pathService).replaceActivePath(org.mockito.ArgumentMatchers.eq(42L), draftCaptor.capture());
        LearningPathDTO.PathDraft draft = draftCaptor.getValue();
        assertThat(draft.getCourseKey()).isEqualTo("python");
        assertThat(draft.getGoal()).isEqualTo("掌握 Python 列表切片");
        assertThat(draft.getProfileDigest()).startsWith("sha256:");
        assertThat(draft.getMasteryDigest()).startsWith("sha256:").hasSizeLessThanOrEqualTo(128);
        assertThat(draft.getSourceMessageId()).isEqualTo(88L);
        assertThat(draft.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSequenceNo()).isEqualTo(1);
            assertThat(item.getKnowledgePoint()).isEqualTo("切片边界");
            assertThat(item.getObjective()).isEqualTo("理解 start、stop、step");
            assertThat(item.getTargetMastery()).isEqualByComparingTo(new BigDecimal("80"));
            assertThat(item.getStatus()).isEqualTo("ready");
        });
    }

    @Test
    void partialDoneRetryAndPersistedReconstructionPreserveTheCompleteResourceSet() {
        service.start(42L, generateRequest(), "Bearer student-token");
        String workflowId = stateStore.only().getWorkflowId();

        Map<String, Object> partial = event(workflowId, 100);
        partial.put("status", "partial");
        partial.put("answer", "列表切片讲义已生成，代码实操待重试");
        partial.put("resources", List.of(
                resource("resource-note-1", "explanation", "列表切片讲义", "knowledge_note")));
        partial.put("failedResources", List.of(Map.of(
                "resourceType", "code_lab",
                "stage", "exporting",
                "retryable", true,
                "message", "代码实操导出失败",
                "errorType", "export_failed"
        )));
        partial.put("pathDraft", pathDraft("初始补强路径"));

        assertThat(eventHandler.get().handle("done", partial)).isTrue();

        LearningPathDTO.WorkflowView partialView = stateStore.only().getView();
        assertThat(partialView.getStatus()).isEqualTo("partial");
        assertThat(partialView.getStage()).isEqualTo("partial");
        assertThat(partialView.getResources()).containsOnlyKeys("knowledge_note");
        assertThat(partialView.getErrors()).containsOnlyKeys("code_lab");
        assertThat(partialView.getErrors().get("code_lab").getMessage()).isEqualTo("代码实操导出失败");
        assertThat(partialView.getErrors().get("code_lab").getRetryable()).isTrue();
        assertThat(partialView.getPath().getId()).isEqualTo(71L);

        ArgumentCaptor<LlmChatResponse> responseCaptor = ArgumentCaptor.forClass(LlmChatResponse.class);
        verify(envelopeService).persistAssistantMessage(
                org.mockito.ArgumentMatchers.eq(42L), any(), responseCaptor.capture(), any(), any(), any());
        assertThat(responseCaptor.getValue().getOutputMeta())
                .containsEntry("status", "partial")
                .containsEntry("failedResourceTypes", List.of("code_lab"));

        LearningPathDTO.WorkflowView retrying = service.retryResource(
                42L, workflowId, "code_lab", "Bearer student-token");
        assertThat(retrying.getResources()).containsOnlyKeys("knowledge_note");
        assertThat(retrying.getPath().getId()).isEqualTo(71L);

        Map<String, Object> recovered = event(workflowId, 100);
        recovered.put("status", "completed");
        recovered.put("answer", "代码实操已补齐");
        recovered.put("resources", List.of(
                resource("resource-code-2", "code_example", "列表切片实操", "code_lab")));
        recovered.put("failedResources", List.of());
        recovered.put("pathDraft", pathDraft("重试返回但不应覆盖的路径"));

        assertThat(eventHandler.get().handle("done", recovered)).isTrue();

        LearningPathDTO.WorkflowView completed = stateStore.only().getView();
        assertThat(completed.getStatus()).isEqualTo("completed");
        assertThat(completed.getErrors()).isEmpty();
        assertThat(completed.getResources()).containsOnlyKeys("knowledge_note", "code_lab");
        assertThat(completed.getResources().get("knowledge_note").getId()).isEqualTo("resource-note-1");
        assertThat(completed.getResources().get("code_lab").getId()).isEqualTo("resource-code-2");
        assertThat(completed.getPath().getId()).isEqualTo(71L);
        verify(pathService).replaceActivePath(anyLong(), any());

        AiLeaderSession persistedSession = session(10L, "learning-" + workflowId);
        persistedSession.setCreateTime(LocalDateTime.now().minusMinutes(2));
        persistedSession.setUpdateTime(LocalDateTime.now());
        AiLeaderMessage userMessage = persistedMessage(
                1L, AiLeaderMessage.ROLE_USER, "列表切片", "{}", "[]");
        AiLeaderMessage partialMessage = persistedMessage(
                88L,
                AiLeaderMessage.ROLE_ASSISTANT,
                "列表切片讲义已生成，代码实操待重试",
                "{\"status\":\"partial\",\"intent\":\"resource_package\","
                        + "\"failedResourceTypes\":[\"code_lab\"]}",
                "[" + jsonResource(
                        "resource-note-1", "explanation", "列表切片讲义", "knowledge_note") + "]");
        AiLeaderMessage recoveredMessage = persistedMessage(
                89L,
                AiLeaderMessage.ROLE_ASSISTANT,
                "代码实操已补齐",
                "{\"status\":\"completed\",\"intent\":\"resource_package\","
                        + "\"failedResourceTypes\":[]}",
                "[" + jsonResource(
                        "resource-code-2", "code_example", "列表切片实操", "code_lab") + "]");
        when(sessionRepository.findByUserIdAndSessionId(42L, "learning-" + workflowId))
                .thenReturn(Optional.of(persistedSession));
        when(messageRepository.findByLeaderSessionIdOrderByCreateTimeAsc(10L))
                .thenReturn(List.of(userMessage, partialMessage, recoveredMessage));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        doAnswer(invocation -> {
            AiLeaderMessage message = invocation.getArgument(0);
            AiLeaderMessageItem item = invocation.getArgument(1);
            item.setResources(mapper.readValue(
                    message.getResourcesJson(),
                    mapper.getTypeFactory().constructCollectionType(List.class, AssistantResourceDTO.class)));
            return null;
        }).when(envelopeService).restoreEnvelope(any(), any(), anyString());

        stateStore.clear();
        LearningPathDTO.WorkflowView reconstructed = service.getWorkflow(42L, workflowId);

        assertThat(reconstructed.getStatus()).isEqualTo("completed");
        assertThat(reconstructed.getMessage()).isEqualTo("代码实操已补齐");
        assertThat(reconstructed.getMessageId()).isEqualTo(89L);
        assertThat(reconstructed.getResources()).containsOnlyKeys("knowledge_note", "code_lab");
        assertThat(reconstructed.getResources().get("knowledge_note").getId())
                .isEqualTo("resource-note-1");
        assertThat(reconstructed.getResources().get("code_lab").getId())
                .isEqualTo("resource-code-2");
    }

    @Test
    void doneUsesSanitizedAnswerAndFailureMessagesForThePublicWorkflowView() {
        service.start(42L, generateRequest(), "Bearer student-token");
        String workflowId = stateStore.only().getWorkflowId();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> failure = invocation.getArgument(1);
            failure.remove("message");
            return null;
        }).when(envelopeService).sanitizeLearningSseEventPayload(
                org.mockito.ArgumentMatchers.eq("agent_failed"), anyMap(), any());
        doAnswer(invocation -> {
                    LlmChatResponse response = invocation.getArgument(0);
                    response.setAnswer("资源生成已完成，部分内容可重试");
                    response.setResources(List.of());
                    return new AssistantEnvelopeService.PreparedEnvelope(List.of(), java.util.Set.of());
                }).when(envelopeService).prepareLiveResponse(any(), anyMap(), anyString(), any());

        Map<String, Object> done = event(workflowId, 100);
        done.put("status", "partial");
        done.put("message", "internal-capability-secret");
        done.put("answer", "internal-capability-secret");
        done.put("resources", List.of());
        done.put("failedResources", List.of(Map.of(
                "resourceType", "code_lab",
                "stage", "exporting",
                "retryable", true,
                "message", "internal-capability-secret",
                "errorType", "export_failed"
        )));
        done.put("pathDraft", pathDraft("安全过滤回归路径"));

        assertThat(eventHandler.get().handle("done", done)).isTrue();

        LearningPathDTO.WorkflowView view = stateStore.only().getView();
        assertThat(view.getMessage()).isEqualTo("资源生成已完成，部分内容可重试");
        assertThat(view.getErrors().get("code_lab").getMessage()).isEqualTo("资源生成失败，请重试");
        verify(envelopeService).sanitizeLearningSseEventPayload(
                org.mockito.ArgumentMatchers.eq("agent_failed"), anyMap(), any());
    }

    @Test
    void workflowOwnershipIsCheckedBeforeReturningOrRetrying() {
        service.start(42L, generateRequest(), "Bearer student-token");
        String workflowId = stateStore.only().getWorkflowId();

        BusinessException read = assertThrows(BusinessException.class,
                () -> service.getWorkflow(99L, workflowId));
        BusinessException retry = assertThrows(BusinessException.class,
                () -> service.retryResource(99L, workflowId, "knowledge_note", "Bearer other"));

        assertThat(read.getCode()).isEqualTo(404);
        assertThat(retry.getCode()).isEqualTo(404);
    }

    @Test
    void fixedProfileQuestionCreatesEvidenceWithoutAcceptingAClientScore() {
        UserProfileEvidence evidence = new UserProfileEvidence();
        evidence.setSourceId("python_weak_topic");
        when(evidenceRepository.findByUserIdAndSourceTypeOrderByCreateTimeAsc(42L, "profile_form"))
                .thenReturn(List.of(evidence));
        LearningPathDTO.ProfileAnswerRequest request = new LearningPathDTO.ProfileAnswerRequest();
        request.setQuestionId("python_weak_topic");
        request.setAnswer("列表切片");

        LearningPathDTO.ProfileAnswerResult result = service.answerProfile(
                42L, request, "Bearer student-token");

        ArgumentCaptor<UserProfileDTO.EvidenceRequest> evidenceCaptor =
                ArgumentCaptor.forClass(UserProfileDTO.EvidenceRequest.class);
        verify(profileService).addEvidence(org.mockito.ArgumentMatchers.eq(42L), evidenceCaptor.capture());
        UserProfileDTO.EvidenceRequest submitted = evidenceCaptor.getValue();
        assertThat(submitted.getDimensionKey()).isEqualTo("weak_points");
        assertThat(submitted.getSourceType()).isEqualTo("profile_form");
        assertThat(submitted.getSourceId()).isEqualTo("python_weak_topic");
        assertThat(submitted.getEvidence()).contains("列表切片");
        assertThat(submitted.getSuggestedDelta()).isNull();
        assertThat(submitted.getConfidence()).isNull();
        assertThat(result.getAnsweredQuestionIds()).containsExactly("python_weak_topic");
        assertThat(result.getProfileCompleteness()).isEqualTo(20);
    }

    private LearningPathDTO.GenerateRequest generateRequest() {
        return request("python", "resource_package", List.of("knowledge_note", "code_lab"));
    }

    private LearningPathDTO.GenerateRequest request(String course, String intent, List<String> types) {
        LearningPathDTO.GenerateRequest request = new LearningPathDTO.GenerateRequest();
        request.setCourseKey(course);
        request.setTopic("列表切片");
        request.setIntent(intent);
        request.setRequestedResourceTypes(types);
        return request;
    }

    private LearningPathDTO.HomeView home() {
        LearningPathDTO.MasteryView mastery = new LearningPathDTO.MasteryView();
        mastery.setKnowledgePointKey("python.lists.slicing");
        mastery.setScore(new BigDecimal("40"));
        LearningPathDTO.PathView path = new LearningPathDTO.PathView();
        path.setId(70L);
        path.setCourseKey("python");
        path.setGoal("补强列表切片");
        path.setVersion(1);
        path.setItems(List.of());
        LearningPathDTO.HomeView home = new LearningPathDTO.HomeView();
        home.setUserId(42L);
        home.setCourseKey("python");
        home.setMastery(List.of(mastery));
        home.setActivePath(path);
        return home;
    }

    private LearningKnowledgeDTO.RetrieveResponse retrieval() {
        LearningKnowledgeDTO.Reference reference = new LearningKnowledgeDTO.Reference();
        reference.setId("ref-list-slicing");
        reference.setTitle("列表切片");
        reference.setContent("切片使用 start、stop 和 step。");
        reference.setSource("Python 程序设计");
        LearningKnowledgeDTO.RetrieveResponse response = new LearningKnowledgeDTO.RetrieveResponse();
        response.setCourseKey("python");
        response.setReferences(List.of(reference));
        return response;
    }

    private UserProfileDTO.RadarSnapshot profileSnapshot() {
        UserProfileDTO.RadarSnapshot snapshot = new UserProfileDTO.RadarSnapshot();
        snapshot.setUserId(42L);
        snapshot.setOverallScore(72);
        snapshot.setDimensions(List.of());
        return snapshot;
    }

    private Map<String, Object> event(String workflowId, int progress) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("workflowId", workflowId);
        event.put("stage", "running");
        event.put("progress", progress);
        event.put("message", "处理中");
        return event;
    }

    private Map<String, Object> resource(
            String id, String kind, String title, String resourceType) {
        return Map.of(
                "schemaVersion", "assistant-resource-v1",
                "id", id,
                "kind", kind,
                "title", title,
                "metadata", Map.of("resourceType", resourceType, "courseKey", "python")
        );
    }

    private Map<String, Object> pathDraft(String goal) {
        return Map.of(
                "title", goal,
                "goal", goal,
                "personalizationReasons", List.of("当前掌握度偏低"),
                "items", List.of(Map.of(
                        "order", 1,
                        "title", "切片边界",
                        "goal", "理解 start、stop、step",
                        "evidenceIds", List.of("ref-list-slicing")
                ))
        );
    }

    private AiLeaderMessage persistedMessage(
            Long id, String role, String content, String outputMetaJson, String resourcesJson) {
        AiLeaderMessage message = new AiLeaderMessage();
        message.setId(id);
        message.setLeaderSessionId(10L);
        message.setRole(role);
        message.setContent(content);
        message.setAnswerType("markdown");
        message.setOutputType("document");
        message.setAgentName("leader_agent");
        message.setOutputMetaJson(outputMetaJson);
        message.setResourcesJson(resourcesJson);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private String jsonResource(String id, String kind, String title, String resourceType) {
        return "{\"schemaVersion\":\"assistant-resource-v1\","
                + "\"id\":\"" + id + "\","
                + "\"kind\":\"" + kind + "\","
                + "\"title\":\"" + title + "\","
                + "\"metadata\":{\"resourceType\":\"" + resourceType
                + "\",\"courseKey\":\"python\"}}";
    }

    private AiLeaderSession session(Long id, String sessionId) {
        AiLeaderSession session = new AiLeaderSession();
        session.setId(id);
        session.setUserId(42L);
        session.setSessionId(sessionId);
        session.setTitle("Python 个性化资源");
        session.setMessageCount(1);
        return session;
    }

    private static final class InMemoryStateStore implements LearningWorkflowStateStore {
        private final Map<String, WorkflowState> states = new LinkedHashMap<>();

        @Override
        public void save(WorkflowState state) {
            states.put(state.getWorkflowId(), state);
        }

        @Override
        public Optional<WorkflowState> find(String workflowId) {
            return Optional.ofNullable(states.get(workflowId));
        }

        private WorkflowState only() {
            assertThat(states).hasSize(1);
            return new ArrayList<>(states.values()).getFirst();
        }

        private void clear() {
            states.clear();
        }
    }
}
