package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AssistantResourceDTO;
import com.example.appbackend.dto.AiLeaderMessageItem;
import com.example.appbackend.dto.LearningKnowledgeDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.dto.LlmChatResponse;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.AiLeaderMessage;
import com.example.appbackend.entity.AiLeaderSession;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.UserProfileEvidence;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AiLeaderMessageRepository;
import com.example.appbackend.repository.AiLeaderSessionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.service.CourseKnowledgeService;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.LearningWorkflowService;
import com.example.appbackend.service.LearningWorkflowStateStore;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class LearningWorkflowServiceImpl implements LearningWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(LearningWorkflowServiceImpl.class);
    private static final String PYTHON = "python";
    private static final String LEADER_AGENT = "leader_agent";
    private static final String PROFILE_SOURCE = "profile_form";
    private static final String SESSION_PREFIX = "learning-";
    private static final String RETRY_RESOURCE_CONTEXT = "retryResourceType";
    private static final String RETRY_CLAIM_CONTEXT = "retryClaimToken";
    private static final Set<String> INTENTS = Set.of(
            "resource_package", "learning_plan", "weakness_review", "path_replanning");
    private static final Set<String> RESOURCE_TYPES = Set.of(
            "knowledge_note", "mind_map", "practice_set", "code_lab",
            "presentation", "extended_reading");
    private static final List<String> DEFAULT_RESOURCE_TYPES = List.of(
            "knowledge_note", "mind_map", "practice_set", "code_lab",
            "presentation", "extended_reading");
    private static final Set<String> LEARNING_EVENTS = Set.of(
            "accepted", "profile", "retrieval", "planning", "agent_start", "agent_done",
            "agent_failed", "review_start", "review_result", "exporting", "pathing",
            "persisting", "retrying", "dependency_unavailable", "error", "done");
    private static final Map<String, String> PROFILE_DIMENSIONS = Map.of(
            "python_goal", "learning_goal",
            "python_level", "ability_performance",
            "python_weak_topic", "weak_points",
            "python_resource_preference", "resource_preference",
            "python_weekly_time", "learning_progress"
    );

    private record DoneOutcome(String status,
                               Map<String, LearningPathDTO.WorkflowError> failures) {
    }

    private record PersistedCompletion(AiLeaderMessage message,
                                       LearningPathDTO.PathView path) {
    }

    private final PythonAiProxyService pythonAiProxyService;
    private final LearningWorkflowStateStore stateStore;
    private final LearningPathService learningPathService;
    private final UserProfileService userProfileService;
    private final CourseKnowledgeService courseKnowledgeService;
    private final UserProfileEvidenceRepository profileEvidenceRepository;
    private final AiLeaderSessionRepository sessionRepository;
    private final AiLeaderMessageRepository messageRepository;
    private final AssistantEnvelopeService assistantEnvelopeService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public LearningWorkflowServiceImpl(PythonAiProxyService pythonAiProxyService,
                                       LearningWorkflowStateStore stateStore,
                                       LearningPathService learningPathService,
                                       UserProfileService userProfileService,
                                       CourseKnowledgeService courseKnowledgeService,
                                       UserProfileEvidenceRepository profileEvidenceRepository,
                                       AiLeaderSessionRepository sessionRepository,
                                       AiLeaderMessageRepository messageRepository,
                                       AssistantEnvelopeService assistantEnvelopeService,
                                       ObjectMapper objectMapper,
                                       PlatformTransactionManager transactionManager) {
        this(pythonAiProxyService, stateStore, learningPathService, userProfileService,
                courseKnowledgeService, profileEvidenceRepository, sessionRepository,
                messageRepository, assistantEnvelopeService, objectMapper,
                new TransactionTemplate(transactionManager));
    }

    LearningWorkflowServiceImpl(PythonAiProxyService pythonAiProxyService,
                                LearningWorkflowStateStore stateStore,
                                LearningPathService learningPathService,
                                UserProfileService userProfileService,
                                CourseKnowledgeService courseKnowledgeService,
                                UserProfileEvidenceRepository profileEvidenceRepository,
                                AiLeaderSessionRepository sessionRepository,
                                AiLeaderMessageRepository messageRepository,
                                AssistantEnvelopeService assistantEnvelopeService,
                                ObjectMapper objectMapper) {
        this(pythonAiProxyService, stateStore, learningPathService, userProfileService,
                courseKnowledgeService, profileEvidenceRepository, sessionRepository,
                messageRepository, assistantEnvelopeService, objectMapper,
                (TransactionTemplate) null);
    }

    private LearningWorkflowServiceImpl(PythonAiProxyService pythonAiProxyService,
                                        LearningWorkflowStateStore stateStore,
                                        LearningPathService learningPathService,
                                        UserProfileService userProfileService,
                                        CourseKnowledgeService courseKnowledgeService,
                                        UserProfileEvidenceRepository profileEvidenceRepository,
                                        AiLeaderSessionRepository sessionRepository,
                                        AiLeaderMessageRepository messageRepository,
                                        AssistantEnvelopeService assistantEnvelopeService,
                                        ObjectMapper objectMapper,
                                        TransactionTemplate transactionTemplate) {
        this.pythonAiProxyService = pythonAiProxyService;
        this.stateStore = stateStore;
        this.learningPathService = learningPathService;
        this.userProfileService = userProfileService;
        this.courseKnowledgeService = courseKnowledgeService;
        this.profileEvidenceRepository = profileEvidenceRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.assistantEnvelopeService = assistantEnvelopeService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public SseEmitter start(Long userId,
                            LearningPathDTO.GenerateRequest request,
                            String authorization) {
        validateGenerateRequest(userId, request);
        String workflowId = UUID.randomUUID().toString();
        String topic = request.getTopic().trim();
        String intent = normalizedIntent(request.getIntent());
        List<String> requestedTypes = normalizedResourceTypes(request.getRequestedResourceTypes());

        Map<String, Object> profileSnapshot = safeProfileContext(userId, authorization);
        LearningPathDTO.HomeView home = learningPathService.getHome(userId, PYTHON);
        List<LearningPathDTO.MasteryView> mastery = home == null || home.getMastery() == null
                ? List.of() : home.getMastery();
        LearningPathDTO.PathView activePath = home == null ? null : home.getActivePath();
        AiLeaderSession session = createWorkflowSession(userId, workflowId, topic);
        LearningWorkflowStateStore.WorkflowState state = newState(
                workflowId, userId, topic, intent, session.getId());

        List<LearningKnowledgeDTO.Reference> references;
        try {
            references = retrieveReferences(topic);
        } catch (RuntimeException error) {
            markDependencyUnavailable(state);
            return dependencyUnavailableEmitter(state.getView());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("courseKey", PYTHON);
        metadata.put("workflowId", workflowId);
        metadata.put("profileSnapshot", profileSnapshot);
        metadata.put("masterySnapshot", mastery);
        metadata.put("pathSnapshot", activePath == null ? Map.of() : activePath);
        metadata.put("references", references);
        metadata.put("requestedResourceTypes", requestedTypes);

        Map<String, Object> pythonRequest = new LinkedHashMap<>();
        pythonRequest.put("input", topic);
        pythonRequest.put("intent", intent);
        pythonRequest.put("agentName", LEADER_AGENT);
        pythonRequest.put("metadata", metadata);
        state.setContext(Map.of("pythonRequest", pythonRequest));
        stateStore.save(state);
        return startPythonStream(state, pythonRequest, authorization);
    }

    @Override
    public LearningPathDTO.WorkflowView getWorkflow(Long userId, String workflowId) {
        validateUserAndWorkflowId(userId, workflowId);
        LearningWorkflowStateStore.WorkflowState state = stateStore.find(workflowId.trim())
                .orElseGet(() -> reconstructCompletedState(userId, workflowId.trim()));
        requireOwner(state, userId);
        return copyView(state.getView());
    }

    @Override
    public LearningPathDTO.HomeView getPythonHome(Long userId, String authorization) {
        requireUser(userId);
        LearningPathDTO.HomeView home = learningPathService.getHome(userId, PYTHON);
        if (home == null) {
            home = new LearningPathDTO.HomeView();
            home.setUserId(userId);
            home.setCourseKey(PYTHON);
            home.setMastery(List.of());
        }
        UserProfileDTO.RadarSnapshot profile = userProfileService.getSnapshot(userId, safeAuthorization(authorization));
        List<String> answered = answeredQuestionIds(userId);
        home.setProfile(profile);
        home.setAnsweredQuestionIds(answered);
        home.setProfileCompleteness(profileCompleteness(answered));
        List<LearningPathDTO.PathItemView> items = home.getActivePath() == null
                || home.getActivePath().getItems() == null
                ? List.of() : home.getActivePath().getItems();
        home.setTodayTasks(items.stream()
                .filter(item -> !"completed".equals(item.getStatus()))
                .sorted(Comparator.comparing(LearningPathDTO.PathItemView::getSequenceNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .limit(3)
                .toList());
        home.setRecommendations(recommendations(items));
        return home;
    }

    @Override
    public LearningPathDTO.ProfileAnswerResult answerProfile(
            Long userId,
            LearningPathDTO.ProfileAnswerRequest request,
            String authorization) {
        requireUser(userId);
        if (request == null || !PROFILE_DIMENSIONS.containsKey(request.getQuestionId())
                || !StringUtils.hasText(request.getAnswer())) {
            throw badRequest("画像问题或回答无效");
        }
        String questionId = request.getQuestionId();
        String answer = request.getAnswer().trim();
        UserProfileDTO.EvidenceRequest evidence = new UserProfileDTO.EvidenceRequest();
        evidence.setDimensionKey(PROFILE_DIMENSIONS.get(questionId));
        evidence.setSourceType(PROFILE_SOURCE);
        evidence.setSourceId(questionId);
        evidence.setAction("answered");
        evidence.setObjectType("python_profile_question");
        evidence.setObjectId(questionId);
        evidence.setObjectName(profileQuestionLabel(questionId));
        evidence.setResult("学生已完成 Python 学习画像补问");
        evidence.setEvidence(profileQuestionLabel(questionId) + "：" + answer);
        evidence.setOccurredAt(LocalDateTime.now());
        evidence.setMetadata(Map.of(
                "courseKey", PYTHON,
                "questionId", questionId,
                "serverOwnedScoring", true
        ));
        // No client-provided score, confidence or delta is accepted here.
        userProfileService.addEvidence(userId, evidence);

        List<String> answered = answeredQuestionIds(userId);
        if (!answered.contains(questionId)) {
            answered = new ArrayList<>(answered);
            answered.add(questionId);
            answered = orderedQuestionIds(answered);
        }
        LearningPathDTO.ProfileAnswerResult result = new LearningPathDTO.ProfileAnswerResult();
        result.setProfile(userProfileService.getSnapshot(userId, safeAuthorization(authorization)));
        result.setAnsweredQuestionIds(answered);
        result.setProfileCompleteness(profileCompleteness(answered));
        return result;
    }

    @Override
    public LearningPathDTO.PathView getPythonPath(Long userId) {
        requireUser(userId);
        return learningPathService.getActivePath(userId, PYTHON);
    }

    @Override
    public List<LearningPathDTO.Recommendation> getPythonRecommendations(Long userId) {
        requireUser(userId);
        LearningPathDTO.PathView path = learningPathService.getActivePath(userId, PYTHON);
        return recommendations(path == null || path.getItems() == null ? List.of() : path.getItems());
    }

    @Override
    public LearningPathDTO.PathItemView recordRecommendationInteraction(
            Long userId, Long itemId, LearningPathDTO.InteractionRequest request) {
        requireUser(userId);
        return learningPathService.recordResourceInteraction(userId, itemId, request);
    }

    @Override
    public LearningPathDTO.PathItemView startPathItem(Long userId, Long itemId) {
        LearningPathDTO.InteractionRequest request = new LearningPathDTO.InteractionRequest();
        request.setAction("open");
        return recordRecommendationInteraction(userId, itemId, request);
    }

    @Override
    public LearningPathDTO.PathItemView completePathItem(Long userId, Long itemId) {
        LearningPathDTO.InteractionRequest request = new LearningPathDTO.InteractionRequest();
        request.setAction("complete");
        return recordRecommendationInteraction(userId, itemId, request);
    }

    @Override
    public LearningPathDTO.PathView replanPythonPath(Long userId, String authorization) {
        requireUser(userId);
        LearningPathDTO.HomeView home = learningPathService.getHome(userId, PYTHON);
        Map<String, Object> profile = safeProfileContext(userId, authorization);
        LearningPathDTO.PathDraft draft = replanDraft(home, profile);
        return learningPathService.replaceActivePath(userId, draft);
    }

    @Override
    public LearningPathDTO.WorkflowView retryResource(
            Long userId, String workflowId, String resourceType, String authorization) {
        validateUserAndWorkflowId(userId, workflowId);
        if (!RESOURCE_TYPES.contains(resourceType)) {
            throw badRequest("不支持的学习资源类型");
        }
        String normalizedWorkflowId = workflowId.trim();
        LearningWorkflowStateStore.WorkflowState observedState = stateStore.find(normalizedWorkflowId)
                .orElseThrow(() -> notFound("学习工作流不存在"));
        requireOwner(observedState, userId);
        synchronized (observedState) {
            if (committedResource(observedState.getView(), resourceType)) {
                return copyView(observedState.getView());
            }
            validateRetryCandidate(observedState, resourceType);
        }
        String claimToken = stateStore.claimRetry(normalizedWorkflowId, resourceType)
                .orElseThrow(() -> conflict("该学习资源正在重试，请勿重复提交"));
        boolean fencedWriteAttempted = false;
        try {
            LearningWorkflowStateStore.WorkflowState state = stateStore
                    .findAuthoritatively(normalizedWorkflowId)
                    .orElseThrow(() -> notFound("学习工作流不存在"));
            requireOwner(state, userId);
            Map<String, Object> pythonRequest;
            synchronized (state) {
                if (committedResource(state.getView(), resourceType)) {
                    stateStore.releaseRetryClaim(
                            normalizedWorkflowId, resourceType, claimToken);
                    return copyView(state.getView());
                }
                if (hasRetryLeaseContext(state)) {
                    String previousResourceType = retryResourceType(state);
                    String previousClaimToken = retryClaimToken(state);
                    if (stateStore.isRetryClaimOwner(
                            normalizedWorkflowId,
                            previousResourceType,
                            previousClaimToken)) {
                        throw conflict("该学习资源正在重试，请勿重复提交");
                    }
                    restoreRetryAfterFailure(state, null);
                }
                validateRetryEligibility(state, resourceType);
                pythonRequest = retryPythonRequest(
                        state, userId, resourceType, authorization);
                Map<String, Object> metadata = mapValue(pythonRequest.get("metadata"));
                metadata.put("requestedResourceTypes", List.of(resourceType));
                pythonRequest.put("metadata", metadata);
                state.setLastProgress(0);
                state.setTerminal(false);
                state.getView().setStatus("partial");
                state.getView().setStage("retrying");
                state.getView().setActiveResourceType(resourceType);
                state.getView().setUpdatedAt(LocalDateTime.now());
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("pythonRequest", pythonRequest);
                context.put(RETRY_RESOURCE_CONTEXT, resourceType);
                context.put(RETRY_CLAIM_CONTEXT, claimToken);
                state.setContext(context);
                fencedWriteAttempted = true;
                if (!stateStore.saveRetryState(state, resourceType, claimToken)) {
                    throw new IllegalStateException(
                            "learning workflow retry state persistence failed");
                }
            }
            startPythonStream(state, pythonRequest, authorization);
            return copyView(state.getView());
        } catch (RuntimeException error) {
            if (!fencedWriteAttempted) {
                stateStore.releaseRetryClaim(
                        normalizedWorkflowId, resourceType, claimToken);
            }
            throw error;
        }
    }

    private SseEmitter startPythonStream(LearningWorkflowStateStore.WorkflowState state,
                                         Map<String, Object> pythonRequest,
                                         String authorization) {
        Set<String> internalCapabilities = new LinkedHashSet<>();
        try {
            return pythonAiProxyService.streamLearningWorkflow(
                    pythonRequest,
                    safeAuthorization(authorization),
                    (eventName, eventPayload) -> handleLearningEvent(
                            state, eventName, eventPayload, internalCapabilities));
        } catch (RuntimeException error) {
            String retryResourceType = retryResourceType(state);
            if (StringUtils.hasText(retryResourceType)) {
                String claimToken = retryClaimToken(state);
                synchronized (state) {
                    restoreRetryAfterFailure(state, "学习资源重试未启动，请稍后再试。");
                    if (!stateStore.completeRetryState(
                            state, retryResourceType, claimToken)) {
                        throw new IllegalStateException(
                                "learning workflow retry failure state persistence failed",
                                error);
                    }
                }
                return dependencyUnavailableEmitter(state.getView());
            }
            markDependencyUnavailable(state);
            return dependencyUnavailableEmitter(state.getView());
        }
    }

    private boolean handleLearningEvent(LearningWorkflowStateStore.WorkflowState state,
                                        String eventName,
                                        Object eventPayload,
                                        Set<String> internalCapabilities) {
        if (!LEARNING_EVENTS.contains(eventName) || !(eventPayload instanceof Map<?, ?> raw)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) raw;
        synchronized (state) {
            if (Boolean.TRUE.equals(state.getTerminal())) {
                return false;
            }
            String retryResourceType = retryResourceType(state);
            String retryClaimToken = retryClaimToken(state);
            if (StringUtils.hasText(retryResourceType)
                    && !stateStore.renewRetryClaim(
                    state.getWorkflowId(), retryResourceType, retryClaimToken)) {
                return false;
            }
            String suppliedWorkflowId = text(payload.get("workflowId"), 80);
            if (StringUtils.hasText(suppliedWorkflowId)
                    && !state.getWorkflowId().equals(suppliedWorkflowId)) {
                return false;
            }
            payload.put("workflowId", state.getWorkflowId());
            AssistantEnvelopeService.CapabilityScan discovered =
                    assistantEnvelopeService.scanInternalCapabilities(payload);
            AssistantEnvelopeService.CapabilityScan merged =
                    assistantEnvelopeService.mergeInternalCapabilities(
                            internalCapabilities, discovered.values());
            if (discovered.malformed() || merged.malformed()) {
                throw new IllegalStateException("learning workflow capability manifest invalid");
            }
            internalCapabilities.clear();
            internalCapabilities.addAll(merged.values());

            if (!"done".equals(eventName)) {
                assistantEnvelopeService.sanitizeLearningSseEventPayload(
                        eventName, payload, Set.copyOf(internalCapabilities));
            }
            int progress = progress(payload.get("progress"), value(state.getLastProgress()));
            if ("done".equals(eventName) && progress != 100) {
                throw new IllegalStateException("learning workflow done progress invalid");
            }
            payload.put("progress", progress);
            state.setLastProgress(progress);
            if ("error".equals(eventName) && StringUtils.hasText(retryResourceType)) {
                restoreRetryAfterFailure(state,
                        firstNonBlank(text(payload.get("message"), 1_000),
                                "学习资源重试中断，请稍后再试。"));
                replacePayload(payload, state.getView());
                if (!stateStore.completeRetryState(
                        state, retryResourceType, retryClaimToken)) {
                    throw new IllegalStateException(
                            "learning workflow retry error state persistence failed");
                }
                return true;
            }
            if ("done".equals(eventName)) {
                if (StringUtils.hasText(retryResourceType)
                        && !stateStore.renewRetryClaim(
                        state.getWorkflowId(), retryResourceType, retryClaimToken)) {
                    return false;
                }
                try {
                    completeWorkflow(state, payload, Set.copyOf(internalCapabilities));
                } catch (RuntimeException error) {
                    if (StringUtils.hasText(retryResourceType)) {
                        restoreRetryAfterFailure(
                                state, "学习资源重试提交失败，请稍后再试。");
                    } else {
                        markCompletionFailure(state);
                    }
                    replacePayload(payload, state.getView());
                    if (StringUtils.hasText(retryResourceType)) {
                        if (!stateStore.completeRetryState(
                                state, retryResourceType, retryClaimToken)) {
                            throw new IllegalStateException(
                                    "learning workflow retry completion failure state persistence failed",
                                    error);
                        }
                    } else {
                        stateStore.save(state);
                    }
                    throw error;
                }
                replacePayload(payload, state.getView());
                state.setTerminal(true);
                if (StringUtils.hasText(retryResourceType)) {
                    if (!stateStore.completeRetryState(
                            state, retryResourceType, retryClaimToken)) {
                        throw new IllegalStateException(
                                "learning workflow retry completion state persistence failed");
                    }
                } else {
                    stateStore.save(state);
                }
                return true;
            }

            if (StringUtils.hasText(retryResourceType)) {
                updateRetryProgressState(state.getView(), eventName, payload, progress);
                if (!stateStore.saveRetryState(
                        state, retryResourceType, retryClaimToken)) {
                    throw new IllegalStateException(
                            "learning workflow retry progress state persistence failed");
                }
                return true;
            }
            updateProgressState(state.getView(), eventName, payload, progress);
            captureIncrementalResource(state.getView(), payload);
            stateStore.save(state);
            return true;
        }
    }

    private void completeWorkflow(LearningWorkflowStateStore.WorkflowState state,
                                  Map<String, Object> payload,
                                  Set<String> internalCapabilities) {
        AiLeaderSession session = sessionRepository.findById(state.getSessionDatabaseId())
                .filter(value -> state.getOwnerUserId().equals(value.getUserId()))
                .orElseThrow(() -> new IllegalStateException("learning workflow session unavailable"));
        DoneOutcome outcome = doneOutcome(state, payload, internalCapabilities);
        LlmChatResponse response = toLearningResponse(state, payload, outcome.status());
        AssistantEnvelopeService.PreparedEnvelope envelope = assistantEnvelopeService.prepareLiveResponse(
                response, payload, state.getView().getTopic(), internalCapabilities);

        String retryResourceType = retryResourceType(state);
        Set<String> expectedTypes = stateRequestedResourceTypes(state);
        Map<String, AssistantResourceDTO> generatedResources = resourceMap(response.getResources());
        generatedResources.keySet().removeIf(type -> !expectedTypes.contains(type));
        response.setResources(new ArrayList<>(generatedResources.values()));

        LearningPathDTO.WorkflowView view = state.getView();
        Map<String, AssistantResourceDTO> mergedResources = new LinkedHashMap<>();
        if (StringUtils.hasText(retryResourceType) && view.getResources() != null) {
            mergedResources.putAll(view.getResources());
            mergedResources.remove(retryResourceType);
        }
        mergedResources.putAll(generatedResources);

        Map<String, LearningPathDTO.WorkflowError> errors = copyErrors(view.getErrors());
        generatedResources.keySet().forEach(errors::remove);
        errors.putAll(outcome.failures());
        for (String expectedType : expectedTypes) {
            if (!generatedResources.containsKey(expectedType)
                    && !outcome.failures().containsKey(expectedType)) {
                errors.put(expectedType, workflowError("资源生成结果缺失，请重试", true));
            }
        }
        String finalStatus = errors.isEmpty() && "completed".equals(outcome.status())
                ? "completed" : "partial";
        response.setOutputMeta(learningOutputMeta(state, finalStatus, errors));
        LearningPathDTO.PathDraft pathDraft = null;
        if (!StringUtils.hasText(retryResourceType)) {
            pathDraft = mapPythonPathDraft(
                    state, payload, response, null, internalCapabilities);
            learningPathService.validatePathDraft(state.getOwnerUserId(), pathDraft);
        } else if (view.getPath() == null) {
            throw new IllegalStateException("learning workflow retry path unavailable");
        }

        LearningPathDTO.PathDraft validatedDraft = pathDraft;
        PersistedCompletion persisted = inTransaction(() -> {
            LearningPathDTO.PathView path;
            if (validatedDraft == null) {
                path = learningPathService.getPathSnapshot(
                        state.getOwnerUserId(),
                        view.getPath().getId(),
                        view.getPath().getVersion(),
                        view.getPath().getSourceMessageId());
            } else {
                learningPathService.validatePathDraft(state.getOwnerUserId(), validatedDraft);
                path = null;
            }
            AiLeaderMessage reserved = assistantEnvelopeService.reserveAssistantMessage(
                    session, response);
            if (validatedDraft != null) {
                bindPathSourceMessage(validatedDraft, reserved.getId());
                path = learningPathService.replaceActivePath(
                        state.getOwnerUserId(), validatedDraft);
            } else {
                List<String> generatedResourceIds = generatedResources.values().stream()
                        .map(AssistantResourceDTO::getId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
                if (!generatedResourceIds.isEmpty()) {
                    path = learningPathService.appendResourcesToPath(
                            state.getOwnerUserId(),
                            path.getId(),
                            path.getVersion(),
                            path.getSourceMessageId(),
                            generatedResourceIds,
                            reserved.getId());
                }
            }
            response.setOutputMeta(withPathMetadata(response.getOutputMeta(), path));
            AiLeaderMessage message = assistantEnvelopeService.persistAssistantMessage(
                    state.getOwnerUserId(), session, response,
                    envelope.internalAttachments(), envelope.internalCapabilities(), reserved);
            refreshSession(session, response.getAnswer());
            return new PersistedCompletion(message, path);
        });
        AiLeaderMessage message = persisted.message();
        LearningPathDTO.PathView path = persisted.path();

        view.setStatus(finalStatus);
        view.setStage("partial".equals(finalStatus) ? "partial" : "done");
        view.setProgress(100);
        view.setMessage(firstNonBlank(
                response.getAnswer(),
                "partial".equals(finalStatus)
                        ? "部分学习资源已生成，可重试失败项。"
                        : "个性化学习资源已生成。"));
        view.setActiveAgentName(null);
        view.setActiveResourceType(null);
        view.setResources(mergedResources);
        view.setErrors(errors);
        view.setPath(path);
        view.setMessageId(message.getId());
        view.setUpdatedAt(LocalDateTime.now());
        Map<String, Object> context = new LinkedHashMap<>(
                state.getContext() == null ? Map.of() : state.getContext());
        context.remove(RETRY_RESOURCE_CONTEXT);
        context.remove(RETRY_CLAIM_CONTEXT);
        state.setContext(context);
    }

    private LlmChatResponse toLearningResponse(LearningWorkflowStateStore.WorkflowState state,
                                               Map<String, Object> payload,
                                               String status) {
        LlmChatResponse response = new LlmChatResponse();
        response.setSessionId(SESSION_PREFIX + state.getWorkflowId());
        response.setModel("");
        response.setRagStrategy("learning_workflow");
        response.setAgentName(LEADER_AGENT);
        response.setSearchKeyword(state.getView().getTopic());
        response.setAnswer(firstNonBlank(
                text(payload.get("answer"), 12_000),
                "已生成“" + state.getView().getTopic() + "”个性化学习资源包。"));
        response.setAnswerType("markdown");
        response.setOutputType("document");
        response.setOutputTypes(List.of("text", "document"));
        response.setOutputMeta(learningOutputMeta(state, status, Map.of()));
        response.setRetrievalMeta(Map.of(
                "courseKey", PYTHON,
                "source", "maxkb",
                "status", "grounded"
        ));
        response.setTrace(mapList(payload.get("events")));
        response.setAttachments(mapList(payload.get("attachments")));
        return response;
    }

    private LearningPathDTO.PathDraft mapPythonPathDraft(
            LearningWorkflowStateStore.WorkflowState state,
            Map<String, Object> payload,
            LlmChatResponse response,
            Long messageId,
            Set<String> internalCapabilities) {
        Map<String, Object> rawDraft = mapValue(payload.get("pathDraft"));
        if (rawDraft.isEmpty()) {
            rawDraft = mapValue(mapValue(payload.get("result")).get("pathDraft"));
        }
        String goal = assistantEnvelopeService.sanitizeLearningText(
                rawDraft.get("goal"), 500, "", internalCapabilities);
        List<Map<String, Object>> rawItems = mapList(rawDraft.get("items"));
        if (!StringUtils.hasText(goal) || rawItems.isEmpty() || rawItems.size() > 20) {
            throw new IllegalStateException("learning workflow path draft invalid");
        }
        rawItems = rawItems.stream()
                .sorted(Comparator.comparingInt(item -> integer(item.get("order"), Integer.MAX_VALUE)))
                .toList();
        List<String> resourceKinds = new ArrayList<>(stateRequestedResourceTypes(state));
        List<String> resourceIds = response.getResources() == null ? List.of() : response.getResources().stream()
                .map(AssistantResourceDTO::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<String> personalization = stringList(rawDraft.get("personalizationReasons")).stream()
                .map(value -> assistantEnvelopeService.sanitizeLearningText(
                        value, 1_000, "", internalCapabilities))
                .filter(StringUtils::hasText)
                .toList();
        List<LearningPathDTO.PathItemDraft> items = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < rawItems.size(); index++) {
            Map<String, Object> rawItem = rawItems.get(index);
            int order = integer(rawItem.get("order"), -1);
            String title = assistantEnvelopeService.sanitizeLearningText(
                    rawItem.get("title"), 160, "", internalCapabilities);
            String objective = assistantEnvelopeService.sanitizeLearningText(
                    rawItem.get("goal"), 500, "", internalCapabilities);
            if (order != index + 1 || !orders.add(order)
                    || !StringUtils.hasText(title) || !StringUtils.hasText(objective)) {
                throw new IllegalStateException("learning workflow path item invalid");
            }
            LearningPathDTO.PathItemDraft item = new LearningPathDTO.PathItemDraft();
            item.setItemKey(pathItemKey(title, order));
            item.setKnowledgePoint(title);
            item.setObjective(objective);
            item.setTargetMastery(new BigDecimal("80"));
            item.setPriority(order);
            item.setSequenceNo(order);
            item.setResourceKinds(resourceKinds);
            item.setResourceIds(resourceIds);
            item.setStatus(order == 1 ? "ready" : "locked");
            item.setDeliveryStatus(resourceIds.isEmpty() ? "pending" : "available");
            item.setSourceMessageId(messageId);
            item.setRationale(personalization.isEmpty()
                    ? "依据当前画像、掌握度和课程证据规划"
                    : text(personalization.get(Math.min(index, personalization.size() - 1)), 1_000));
            items.add(item);
        }

        Map<String, Object> pythonRequest = copyPythonRequest(state);
        Map<String, Object> metadata = mapValue(pythonRequest.get("metadata"));
        LearningPathDTO.PathDraft draft = new LearningPathDTO.PathDraft();
        draft.setCourseKey(PYTHON);
        draft.setGoal(goal);
        draft.setProfileDigest(digest(metadata.get("profileSnapshot")));
        draft.setMasteryDigest(digest(metadata.get("masterySnapshot")));
        draft.setSourceMessageId(messageId);
        draft.setGeneratedAt(LocalDateTime.now());
        draft.setNextReplanAt(LocalDateTime.now().plusDays(7));
        draft.setItems(items);
        return draft;
    }

    private void bindPathSourceMessage(
            LearningPathDTO.PathDraft draft, Long sourceMessageId) {
        if (draft == null || sourceMessageId == null) {
            throw new IllegalStateException("learning workflow path source message unavailable");
        }
        draft.setSourceMessageId(sourceMessageId);
        if (draft.getItems() != null) {
            draft.getItems().forEach(item -> item.setSourceMessageId(sourceMessageId));
        }
    }

    private Map<String, Object> withPathMetadata(
            Map<String, Object> outputMeta, LearningPathDTO.PathView path) {
        if (path == null || path.getId() == null || path.getVersion() == null
                || path.getSourceMessageId() == null) {
            throw new IllegalStateException("learning workflow persisted path metadata unavailable");
        }
        Map<String, Object> result = new LinkedHashMap<>(
                outputMeta == null ? Map.of() : outputMeta);
        result.put("pathId", path.getId());
        result.put("pathVersion", path.getVersion());
        result.put("pathSourceMessageId", path.getSourceMessageId());
        return result;
    }

    private <T> T inTransaction(Supplier<T> work) {
        if (transactionTemplate == null) {
            return work.get();
        }
        T result = transactionTemplate.execute(status -> work.get());
        if (result == null) {
            throw new IllegalStateException("learning workflow transaction returned no result");
        }
        return result;
    }

    private LearningPathDTO.PathDraft replanDraft(LearningPathDTO.HomeView home,
                                                   Map<String, Object> profile) {
        List<LearningPathDTO.MasteryView> mastery = home == null || home.getMastery() == null
                ? List.of() : home.getMastery();
        LearningPathDTO.PathView current = home == null ? null : home.getActivePath();
        List<LearningPathDTO.PathItemDraft> items = new ArrayList<>();
        if (current != null && current.getItems() != null && !current.getItems().isEmpty()) {
            List<LearningPathDTO.PathItemView> ordered = current.getItems().stream()
                    .sorted(Comparator
                            .comparing((LearningPathDTO.PathItemView item) -> "completed".equals(item.getStatus()))
                            .thenComparing(LearningPathDTO.PathItemView::getPriority,
                                    Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            int sequence = 1;
            for (LearningPathDTO.PathItemView source : ordered) {
                LearningPathDTO.PathItemDraft item = new LearningPathDTO.PathItemDraft();
                item.setItemKey(firstNonBlank(source.getItemKey(), "replan-step-" + sequence));
                item.setKnowledgePoint(firstNonBlank(source.getKnowledgePoint(), "Python 基础"));
                item.setObjective(firstNonBlank(source.getObjective(), "完成个性化复习"));
                item.setTargetMastery(source.getTargetMastery() == null
                        ? new BigDecimal("80") : source.getTargetMastery());
                item.setPriority(sequence);
                item.setSequenceNo(sequence++);
                item.setResourceKinds(source.getResourceKinds());
                item.setResourceIds(source.getResourceIds());
                item.setStatus("completed".equals(source.getStatus()) ? "completed"
                        : item.getSequenceNo() == 1 ? "ready" : "locked");
                item.setDeliveryStatus(source.getDeliveryStatus());
                item.setSourceMessageId(source.getSourceMessageId());
                item.setRationale("依据最新画像和掌握度重新排序");
                items.add(item);
            }
        } else {
            List<String> points = mastery.stream()
                    .sorted(Comparator.comparing(LearningPathDTO.MasteryView::getScore,
                            Comparator.nullsFirst(BigDecimal::compareTo)))
                    .map(item -> firstNonBlank(item.getKnowledgePointName(), item.getKnowledgePointKey()))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .limit(5)
                    .toList();
            if (points.isEmpty()) {
                points = List.of("Python 语法与数据类型", "列表与切片", "函数与模块");
            }
            for (int index = 0; index < points.size(); index++) {
                int sequence = index + 1;
                LearningPathDTO.PathItemDraft item = new LearningPathDTO.PathItemDraft();
                item.setItemKey(pathItemKey(points.get(index), sequence));
                item.setKnowledgePoint(text(points.get(index), 160));
                item.setObjective("理解并练习" + points.get(index));
                item.setTargetMastery(new BigDecimal("80"));
                item.setPriority(sequence);
                item.setSequenceNo(sequence);
                item.setResourceKinds(DEFAULT_RESOURCE_TYPES);
                item.setResourceIds(List.of());
                item.setStatus(sequence == 1 ? "ready" : "locked");
                item.setDeliveryStatus("pending");
                item.setRationale("依据当前薄弱知识点规划");
                items.add(item);
            }
        }
        LearningPathDTO.PathDraft draft = new LearningPathDTO.PathDraft();
        draft.setCourseKey(PYTHON);
        draft.setGoal(current == null ? "建立 Python 个性化学习路径"
                : "动态调整：" + text(current.getGoal(), 460));
        draft.setProfileDigest(digest(profile));
        draft.setMasteryDigest(digest(mastery));
        draft.setGeneratedAt(LocalDateTime.now());
        draft.setNextReplanAt(LocalDateTime.now().plusDays(7));
        draft.setItems(items);
        return draft;
    }

    private LearningWorkflowStateStore.WorkflowState reconstructCompletedState(
            Long userId, String workflowId) {
        AiLeaderSession session = sessionRepository.findByUserIdAndSessionId(
                        userId, SESSION_PREFIX + workflowId)
                .orElseThrow(() -> notFound("学习工作流不存在"));
        List<AiLeaderMessage> messages = messageRepository
                .findByLeaderSessionIdOrderByCreateTimeAscIdAsc(session.getId());
        List<AiLeaderMessage> assistantMessages = messages.stream()
                .filter(item -> AiLeaderMessage.ROLE_ASSISTANT.equals(item.getRole()))
                .toList();
        if (assistantMessages.isEmpty()) {
            throw notFound("学习工作流不存在");
        }
        AiLeaderMessage assistant = assistantMessages.getLast();
        String topic = messages.stream()
                .filter(item -> AiLeaderMessage.ROLE_USER.equals(item.getRole()))
                .map(AiLeaderMessage::getContent)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(session.getTitle());

        Map<String, AssistantResourceDTO> restoredResources = new LinkedHashMap<>();
        AiLeaderMessageItem restored = null;
        Map<String, Object> restoredPathMetadata = Map.of();
        for (AiLeaderMessage persisted : assistantMessages) {
            AiLeaderMessageItem item = restorePersistedMessage(persisted, topic);
            restoredResources.putAll(resourceMap(item.getResources()));
            restored = item;
            if (hasPathMetadata(item.getOutputMeta())) {
                restoredPathMetadata = item.getOutputMeta();
            }
        }
        if (restored == null) {
            throw notFound("学习工作流不存在");
        }
        Map<String, LearningPathDTO.WorkflowError> persistedErrors = persistedErrors(
                restored.getOutputMeta());
        String storedStatus = text(restored.getOutputMeta().get("status"), 40);
        if (!Set.of("completed", "partial").contains(storedStatus)) {
            throw notFound("学习工作流尚未完整持久化");
        }
        String persistedStatus = "partial".equals(storedStatus) || !persistedErrors.isEmpty()
                ? "partial" : "completed";
        LearningPathDTO.PathView restoredPath = restoreWorkflowPath(
                userId, restoredPathMetadata);

        LearningPathDTO.WorkflowView view = new LearningPathDTO.WorkflowView();
        view.setWorkflowId(workflowId);
        view.setCourseKey(PYTHON);
        view.setTopic(topic);
        view.setIntent(firstNonBlank(
                text(restored.getOutputMeta().get("intent"), 80), "resource_package"));
        view.setStatus(persistedStatus);
        view.setStage("partial".equals(persistedStatus) ? "partial" : "done");
        view.setProgress(100);
        view.setMessage(restored.getContent());
        view.setMessageId(assistant.getId());
        view.setResources(restoredResources);
        view.setErrors(persistedErrors);
        view.setPath(restoredPath);
        view.setStartedAt(session.getCreateTime());
        view.setUpdatedAt(session.getUpdateTime());

        LearningWorkflowStateStore.WorkflowState state = new LearningWorkflowStateStore.WorkflowState();
        state.setWorkflowId(workflowId);
        state.setOwnerUserId(userId);
        state.setView(view);
        state.setSessionDatabaseId(session.getId());
        state.setLastProgress(100);
        state.setTerminal(true);
        state.setContext(Map.of());
        stateStore.save(state);
        return state;
    }

    private boolean hasPathMetadata(Map<String, Object> outputMeta) {
        return outputMeta != null
                && outputMeta.get("pathId") instanceof Number
                && outputMeta.get("pathVersion") instanceof Number
                && outputMeta.get("pathSourceMessageId") instanceof Number;
    }

    private LearningPathDTO.PathView restoreWorkflowPath(
            Long userId, Map<String, Object> outputMeta) {
        if (!hasPathMetadata(outputMeta)) {
            return null;
        }
        Number pathId = (Number) outputMeta.get("pathId");
        Number pathVersion = (Number) outputMeta.get("pathVersion");
        Number sourceMessageId = (Number) outputMeta.get("pathSourceMessageId");
        return learningPathService.getPathSnapshot(
                userId, pathId.longValue(), pathVersion.intValue(), sourceMessageId.longValue());
    }

    private AiLeaderMessageItem restorePersistedMessage(
            AiLeaderMessage message, String expectedQuery) {
        AiLeaderMessageItem item = new AiLeaderMessageItem();
        item.setId(message.getId());
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setAnswerType(message.getAnswerType());
        item.setOutputType(message.getOutputType());
        item.setAgentName(message.getAgentName());
        item.setSearchKeyword(message.getSearchKeyword());
        item.setOutputTypes(List.of());
        item.setOutputMeta(readMapJson(message.getOutputMetaJson()));
        item.setRetrievalMeta(Map.of());
        item.setTrace(List.of());
        assistantEnvelopeService.restoreEnvelope(message, item, expectedQuery);
        return item;
    }

    private Map<String, Object> readMapJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(
                    json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException error) {
            log.warn("learning workflow persisted metadata malformed errorType={}",
                    error.getClass().getSimpleName());
            return new LinkedHashMap<>();
        }
    }

    private Map<String, LearningPathDTO.WorkflowError> persistedErrors(
            Map<String, Object> outputMeta) {
        Map<String, LearningPathDTO.WorkflowError> errors = new LinkedHashMap<>();
        for (Map<String, Object> failure : mapList(outputMeta.get("failedResources"))) {
            String type = text(failure.get("resourceType"), 64);
            if (RESOURCE_TYPES.contains(type)) {
                errors.put(type, workflowError(
                        firstNonBlank(text(failure.get("message"), 1_000), "资源生成失败，请重试"),
                        Boolean.TRUE.equals(failure.get("retryable"))));
            }
        }
        for (String type : stringList(outputMeta.get("failedResourceTypes"))) {
            if (RESOURCE_TYPES.contains(type) && !errors.containsKey(type)) {
                errors.put(type, workflowError("资源生成失败，请重试", true));
            }
        }
        return errors;
    }

    private Map<String, AssistantResourceDTO> resourceMap(List<AssistantResourceDTO> resources) {
        Map<String, AssistantResourceDTO> result = new LinkedHashMap<>();
        if (resources == null) {
            return result;
        }
        for (AssistantResourceDTO resource : resources) {
            if (resource == null) {
                continue;
            }
            String type = resource.getMetadata() == null ? ""
                    : text(resource.getMetadata().get("resourceType"), 64);
            if (!RESOURCE_TYPES.contains(type)) {
                type = resourceKindAlias(resource.getKind());
            }
            if (RESOURCE_TYPES.contains(type)) {
                result.putIfAbsent(type, resource);
            }
        }
        return result;
    }

    private void captureIncrementalResource(LearningPathDTO.WorkflowView view,
                                            Map<String, Object> payload) {
        if (!(payload.get("resource") instanceof Map<?, ?> raw)) {
            return;
        }
        try {
            AssistantResourceDTO resource = objectMapper.convertValue(raw, AssistantResourceDTO.class);
            String type = text(payload.get("resourceType"), 64);
            if (RESOURCE_TYPES.contains(type)) {
                if (view.getResources() == null) {
                    view.setResources(new LinkedHashMap<>());
                }
                view.getResources().put(type, resource);
            }
        } catch (IllegalArgumentException ignored) {
            // Sanitizer already rejected malformed resource previews.
        }
    }

    private void updateProgressState(LearningPathDTO.WorkflowView view,
                                     String eventName,
                                     Map<String, Object> payload,
                                     int progress) {
        view.setStage(eventName);
        view.setProgress(progress);
        view.setStatus(switch (eventName) {
            case "accepted" -> "accepted";
            case "dependency_unavailable" -> "dependency_unavailable";
            case "error", "agent_failed" -> "generation_failed";
            default -> "generating";
        });
        view.setActiveAgentName(text(payload.get("agentName"), 80));
        view.setActiveResourceType(text(payload.get("resourceType"), 64));
        view.setMessage(text(payload.get("message"), 1_000));
        if (Set.of("error", "agent_failed").contains(eventName)) {
            String type = firstNonBlank(view.getActiveResourceType(), "workflow");
            LearningPathDTO.WorkflowError error = new LearningPathDTO.WorkflowError();
            error.setMessage(firstNonBlank(view.getMessage(), "资源生成失败"));
            error.setRetryable(Boolean.TRUE.equals(payload.get("retryable")));
            if (view.getErrors() == null) {
                view.setErrors(new LinkedHashMap<>());
            }
            view.getErrors().put(type, error);
        }
        view.setUpdatedAt(LocalDateTime.now());
    }

    private void updateRetryProgressState(LearningPathDTO.WorkflowView view,
                                          String eventName,
                                          Map<String, Object> payload,
                                          int progress) {
        view.setStatus("partial");
        view.setStage("agent_failed".equals(eventName) ? "retrying" : eventName);
        view.setProgress(progress);
        view.setActiveAgentName(text(payload.get("agentName"), 80));
        view.setActiveResourceType(firstNonBlank(
                text(payload.get("resourceType"), 64), view.getActiveResourceType()));
        String message = text(payload.get("message"), 1_000);
        if (StringUtils.hasText(message)) {
            view.setMessage(message);
        }
        view.setUpdatedAt(LocalDateTime.now());
    }

    private void restoreRetryAfterFailure(
            LearningWorkflowStateStore.WorkflowState state, String message) {
        LearningPathDTO.WorkflowView view = state.getView();
        view.setStatus("partial");
        view.setStage("partial");
        view.setProgress(100);
        view.setActiveAgentName(null);
        view.setActiveResourceType(null);
        if (StringUtils.hasText(message)) {
            view.setMessage(text(message, 1_000));
        }
        view.setUpdatedAt(LocalDateTime.now());
        state.setLastProgress(100);
        state.setTerminal(true);
        Map<String, Object> context = new LinkedHashMap<>(
                state.getContext() == null ? Map.of() : state.getContext());
        context.remove(RETRY_RESOURCE_CONTEXT);
        context.remove(RETRY_CLAIM_CONTEXT);
        state.setContext(context);
    }

    private void markCompletionFailure(LearningWorkflowStateStore.WorkflowState state) {
        LearningPathDTO.WorkflowView view = state.getView();
        view.setStatus("generation_failed");
        view.setStage("error");
        view.setProgress(100);
        view.setMessage("学习资源提交失败，未发布不完整结果，请重新生成。");
        view.setActiveAgentName(null);
        view.setActiveResourceType(null);
        Map<String, LearningPathDTO.WorkflowError> errors = copyErrors(view.getErrors());
        errors.put("workflow", workflowError(view.getMessage(), true));
        view.setErrors(errors);
        view.setUpdatedAt(LocalDateTime.now());
        state.setLastProgress(100);
        state.setTerminal(true);
    }

    private void replacePayload(Map<String, Object> payload, LearningPathDTO.WorkflowView view) {
        Map<String, Object> safe = objectMapper.convertValue(
                view, new TypeReference<Map<String, Object>>() { });
        payload.clear();
        payload.putAll(safe);
    }

    private LearningWorkflowStateStore.WorkflowState newState(
            String workflowId, Long userId, String topic, String intent, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        LearningPathDTO.WorkflowView view = new LearningPathDTO.WorkflowView();
        view.setWorkflowId(workflowId);
        view.setCourseKey(PYTHON);
        view.setTopic(topic);
        view.setIntent(intent);
        view.setStatus("accepted");
        view.setStage("accepted");
        view.setProgress(0);
        view.setMessage("学习工作流已受理");
        view.setResources(new LinkedHashMap<>());
        view.setErrors(new LinkedHashMap<>());
        view.setStartedAt(now);
        view.setUpdatedAt(now);

        LearningWorkflowStateStore.WorkflowState state = new LearningWorkflowStateStore.WorkflowState();
        state.setWorkflowId(workflowId);
        state.setOwnerUserId(userId);
        state.setView(view);
        state.setSessionDatabaseId(sessionId);
        state.setLastProgress(0);
        state.setTerminal(false);
        state.setContext(Map.of());
        stateStore.save(state);
        return state;
    }

    private AiLeaderSession createWorkflowSession(Long userId, String workflowId, String topic) {
        AiLeaderSession session = new AiLeaderSession();
        session.setUserId(userId);
        session.setSessionId(SESSION_PREFIX + workflowId);
        session.setTitle(text(topic, 40));
        session.setLastMessage(topic);
        session.setMessageCount(0);
        session = sessionRepository.save(session);
        AiLeaderMessage userMessage = new AiLeaderMessage();
        userMessage.setLeaderSessionId(session.getId());
        userMessage.setRole(AiLeaderMessage.ROLE_USER);
        userMessage.setContent(topic);
        userMessage.setAnswerType("text");
        messageRepository.save(userMessage);
        refreshSession(session, topic);
        return session;
    }

    private void refreshSession(AiLeaderSession session, String lastMessage) {
        session.setLastMessage(text(lastMessage, 500));
        session.setMessageCount((int) messageRepository.countByLeaderSessionId(session.getId()));
        sessionRepository.save(session);
    }

    private List<LearningKnowledgeDTO.Reference> retrieveReferences(String topic) {
        LearningKnowledgeDTO.RetrieveRequest request = new LearningKnowledgeDTO.RetrieveRequest();
        request.setCourseKey(PYTHON);
        request.setQuery(topic);
        request.setTopNumber(12);
        LearningKnowledgeDTO.RetrieveResponse response = courseKnowledgeService.retrieve(request);
        if (response == null || response.getReferences() == null || response.getReferences().isEmpty()) {
            throw new BusinessException(502, "Python 课程知识库暂时不可用");
        }
        return response.getReferences();
    }

    private Map<String, Object> safeProfileContext(Long userId, String authorization) {
        Map<String, Object> context = userProfileService.buildLeaderProfileContext(
                userId, safeAuthorization(authorization));
        return context == null ? Map.of() : new LinkedHashMap<>(context);
    }

    private void markDependencyUnavailable(LearningWorkflowStateStore.WorkflowState state) {
        synchronized (state) {
            state.getView().setStatus("dependency_unavailable");
            state.getView().setStage("dependency_unavailable");
            state.getView().setMessage("学习资源依赖暂时不可用，请稍后恢复此工作流。");
            state.getView().setUpdatedAt(LocalDateTime.now());
            state.setTerminal(false);
            stateStore.save(state);
        }
    }

    private SseEmitter dependencyUnavailableEmitter(LearningPathDTO.WorkflowView view) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("workflowId", view.getWorkflowId());
            payload.put("stage", "dependency_unavailable");
            payload.put("progress", value(view.getProgress()));
            payload.put("message", view.getMessage());
            payload.put("retryable", true);
            emitter.send(SseEmitter.event().name("dependency_unavailable")
                    .data(payload, MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    private List<LearningPathDTO.Recommendation> recommendations(
            List<LearningPathDTO.PathItemView> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(item -> !"completed".equals(item.getStatus())
                        && !"dismissed".equals(item.getDeliveryStatus()))
                .sorted(Comparator
                        .comparing(LearningPathDTO.PathItemView::getPriority,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(LearningPathDTO.PathItemView::getSequenceNo,
                                Comparator.nullsLast(Integer::compareTo)))
                .map(item -> {
                    LearningPathDTO.Recommendation recommendation = new LearningPathDTO.Recommendation();
                    recommendation.setItemId(item.getId());
                    recommendation.setItemKey(item.getItemKey());
                    recommendation.setKnowledgePoint(item.getKnowledgePoint());
                    recommendation.setObjective(item.getObjective());
                    recommendation.setPriority(item.getPriority());
                    recommendation.setResourceIds(item.getResourceIds());
                    recommendation.setStatus(item.getStatus());
                    recommendation.setRationale(item.getRationale());
                    return recommendation;
                })
                .toList();
    }

    private List<String> answeredQuestionIds(Long userId) {
        return orderedQuestionIds(profileEvidenceRepository
                .findByUserIdAndSourceTypeOrderByCreateTimeAsc(userId, PROFILE_SOURCE).stream()
                .map(UserProfileEvidence::getSourceId)
                .filter(PROFILE_DIMENSIONS::containsKey)
                .distinct()
                .toList());
    }

    private List<String> orderedQuestionIds(List<String> values) {
        Set<String> answered = new LinkedHashSet<>(values == null ? List.of() : values);
        return LearningPathDTO.PYTHON_PROFILE_QUESTION_IDS.stream()
                .filter(answered::contains)
                .sorted(Comparator.comparingInt(this::questionOrder))
                .toList();
    }

    private int questionOrder(String value) {
        return switch (value) {
            case "python_goal" -> 0;
            case "python_level" -> 1;
            case "python_weak_topic" -> 2;
            case "python_resource_preference" -> 3;
            case "python_weekly_time" -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    private int profileCompleteness(List<String> answered) {
        return Math.min(100, (answered == null ? 0 : answered.size()) * 20);
    }

    private String profileQuestionLabel(String questionId) {
        return switch (questionId) {
            case "python_goal" -> "Python 学习目标";
            case "python_level" -> "Python 当前基础";
            case "python_weak_topic" -> "Python 薄弱知识点";
            case "python_resource_preference" -> "Python 资源偏好";
            case "python_weekly_time" -> "Python 每周学习时间";
            default -> "Python 学习画像";
        };
    }

    private List<String> normalizedResourceTypes(List<String> values) {
        List<String> source = values == null || values.isEmpty() ? DEFAULT_RESOURCE_TYPES : values;
        List<String> normalized = source.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.size() > 6
                || normalized.stream().anyMatch(value -> !RESOURCE_TYPES.contains(value))) {
            throw badRequest("学习资源类型无效");
        }
        return normalized;
    }

    private String normalizedIntent(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "resource_package";
        if (!INTENTS.contains(normalized)) {
            throw badRequest("学习工作流意图无效");
        }
        return normalized;
    }

    private void validateGenerateRequest(Long userId, LearningPathDTO.GenerateRequest request) {
        requireUser(userId);
        if (request == null || !PYTHON.equals(request.getCourseKey())
                || !StringUtils.hasText(request.getTopic())) {
            throw badRequest("仅支持已认证用户的 Python 学习资源生成");
        }
        normalizedIntent(request.getIntent());
        normalizedResourceTypes(request.getRequestedResourceTypes());
    }

    private void validateUserAndWorkflowId(Long userId, String workflowId) {
        requireUser(userId);
        if (!StringUtils.hasText(workflowId) || workflowId.length() > 80) {
            throw badRequest("学习工作流标识无效");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
    }

    private void requireOwner(LearningWorkflowStateStore.WorkflowState state, Long userId) {
        if (state == null || !userId.equals(state.getOwnerUserId())) {
            throw notFound("学习工作流不存在");
        }
    }

    private boolean committedResource(LearningPathDTO.WorkflowView view, String resourceType) {
        return view != null
                && view.getResources() != null
                && view.getResources().containsKey(resourceType)
                && (view.getErrors() == null || !view.getErrors().containsKey(resourceType));
    }

    private void validateRetryCandidate(
            LearningWorkflowStateStore.WorkflowState state, String resourceType) {
        if (hasRetryLeaseContext(state)) {
            validateRetryableResource(state, resourceType);
            return;
        }
        validateRetryEligibility(state, resourceType);
    }

    private boolean hasRetryLeaseContext(LearningWorkflowStateStore.WorkflowState state) {
        LearningPathDTO.WorkflowView view = state == null ? null : state.getView();
        return !Boolean.TRUE.equals(state == null ? null : state.getTerminal())
                && view != null
                && "partial".equals(view.getStatus())
                && "retrying".equals(view.getStage())
                && StringUtils.hasText(retryResourceType(state))
                && StringUtils.hasText(retryClaimToken(state));
    }

    private void validateRetryEligibility(
            LearningWorkflowStateStore.WorkflowState state, String resourceType) {
        LearningPathDTO.WorkflowView view = state.getView();
        if (!Boolean.TRUE.equals(state.getTerminal())
                || view == null
                || !"partial".equals(view.getStatus())
                || "retrying".equals(view.getStage())) {
            throw conflict("学习工作流尚未进入可重试的部分完成状态");
        }
        validateRetryableResource(state, resourceType);
    }

    private void validateRetryableResource(
            LearningWorkflowStateStore.WorkflowState state, String resourceType) {
        LearningPathDTO.WorkflowView view = state.getView();
        LearningPathDTO.WorkflowError error = view.getErrors() == null
                ? null : view.getErrors().get(resourceType);
        if (error == null || !Boolean.TRUE.equals(error.getRetryable())) {
            throw conflict("该学习资源当前不可重试");
        }
    }

    private DoneOutcome doneOutcome(LearningWorkflowStateStore.WorkflowState state,
                                    Map<String, Object> payload,
                                    Set<String> internalCapabilities) {
        Map<String, Object> result = mapValue(payload.get("result"));
        String status = firstNonBlank(
                text(payload.get("status"), 40), text(result.get("status"), 40));
        if (!Set.of("completed", "partial").contains(status)) {
            throw new IllegalStateException("learning workflow done status invalid");
        }
        Object rawFailures = payload.containsKey("failedResources")
                ? payload.get("failedResources") : result.get("failedResources");
        Set<String> expectedTypes = stateRequestedResourceTypes(state);
        Map<String, LearningPathDTO.WorkflowError> failures = new LinkedHashMap<>();
        for (Map<String, Object> failure : mapList(rawFailures)) {
            Map<String, Object> safeFailure = new LinkedHashMap<>(failure);
            assistantEnvelopeService.sanitizeLearningSseEventPayload(
                    "agent_failed", safeFailure, internalCapabilities);
            String type = text(safeFailure.get("resourceType"), 64);
            if (!expectedTypes.contains(type)) {
                continue;
            }
            failures.put(type, workflowError(
                    firstNonBlank(text(safeFailure.get("message"), 1_000), "资源生成失败，请重试"),
                    Boolean.TRUE.equals(safeFailure.get("retryable"))));
        }
        if (!failures.isEmpty()) {
            status = "partial";
        }
        return new DoneOutcome(status, failures);
    }

    private Map<String, Object> learningOutputMeta(
            LearningWorkflowStateStore.WorkflowState state,
            String status,
            Map<String, LearningPathDTO.WorkflowError> errors) {
        List<String> failedTypes = errors == null ? List.of() : errors.keySet().stream()
                .filter(RESOURCE_TYPES::contains)
                .sorted()
                .toList();
        List<Map<String, Object>> failures = failedTypes.stream()
                .map(type -> {
                    LearningPathDTO.WorkflowError error = errors.get(type);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("resourceType", type);
                    item.put("message", firstNonBlank(error.getMessage(), "资源生成失败，请重试"));
                    item.put("retryable", Boolean.TRUE.equals(error.getRetryable()));
                    return item;
                })
                .toList();
        Map<String, Object> outputMeta = new LinkedHashMap<>();
        outputMeta.put("courseKey", PYTHON);
        outputMeta.put("workflowId", state.getWorkflowId());
        outputMeta.put("intent", state.getView().getIntent());
        outputMeta.put("status", status);
        outputMeta.put("failedResourceTypes", failedTypes);
        outputMeta.put("failedResources", failures);
        return outputMeta;
    }

    private Map<String, LearningPathDTO.WorkflowError> copyErrors(
            Map<String, LearningPathDTO.WorkflowError> source) {
        Map<String, LearningPathDTO.WorkflowError> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((type, error) -> {
            if (RESOURCE_TYPES.contains(type) && error != null) {
                copy.put(type, workflowError(error.getMessage(),
                        Boolean.TRUE.equals(error.getRetryable())));
            }
        });
        return copy;
    }

    private LearningPathDTO.WorkflowError workflowError(String message, boolean retryable) {
        LearningPathDTO.WorkflowError error = new LearningPathDTO.WorkflowError();
        error.setMessage(firstNonBlank(text(message, 1_000), "资源生成失败，请重试"));
        error.setRetryable(retryable);
        return error;
    }

    private String retryResourceType(LearningWorkflowStateStore.WorkflowState state) {
        return state.getContext() == null ? ""
                : text(state.getContext().get(RETRY_RESOURCE_CONTEXT), 64);
    }

    private String retryClaimToken(LearningWorkflowStateStore.WorkflowState state) {
        return state.getContext() == null ? ""
                : text(state.getContext().get(RETRY_CLAIM_CONTEXT), 80);
    }

    private Map<String, Object> retryPythonRequest(
            LearningWorkflowStateStore.WorkflowState state,
            Long userId,
            String resourceType,
            String authorization) {
        Object existing = state.getContext() == null
                ? null : state.getContext().get("pythonRequest");
        if (existing instanceof Map<?, ?>) {
            return objectMapper.convertValue(existing,
                    new TypeReference<Map<String, Object>>() { });
        }

        LearningPathDTO.HomeView home = learningPathService.getHome(userId, PYTHON);
        List<LearningPathDTO.MasteryView> mastery = home == null || home.getMastery() == null
                ? List.of() : home.getMastery();
        LearningPathDTO.PathView workflowPath = state.getView() == null
                ? null : state.getView().getPath();
        LearningPathDTO.PathView activePath = workflowPath != null
                ? workflowPath
                : home == null ? null : home.getActivePath();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("courseKey", PYTHON);
        metadata.put("workflowId", state.getWorkflowId());
        metadata.put("profileSnapshot", safeProfileContext(userId, authorization));
        metadata.put("masterySnapshot", mastery);
        metadata.put("pathSnapshot", activePath == null ? Map.of() : activePath);
        metadata.put("references", retrieveReferences(state.getView().getTopic()));
        metadata.put("requestedResourceTypes", List.of(resourceType));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("input", state.getView().getTopic());
        request.put("intent", state.getView().getIntent());
        request.put("agentName", LEADER_AGENT);
        request.put("metadata", metadata);
        return request;
    }

    private Map<String, Object> copyPythonRequest(LearningWorkflowStateStore.WorkflowState state) {
        Object value = state.getContext() == null ? null : state.getContext().get("pythonRequest");
        if (!(value instanceof Map<?, ?>)) {
            throw new BusinessException(409, "学习工作流无法重试");
        }
        return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() { });
    }

    private Set<String> stateRequestedResourceTypes(LearningWorkflowStateStore.WorkflowState state) {
        Map<String, Object> request = copyPythonRequest(state);
        return new LinkedHashSet<>(stringList(mapValue(request.get("metadata"))
                .get("requestedResourceTypes")));
    }

    private LearningPathDTO.WorkflowView copyView(LearningPathDTO.WorkflowView view) {
        return objectMapper.convertValue(view, LearningPathDTO.WorkflowView.class);
    }

    private String digest(Object value) {
        try {
            byte[] canonical = canonicalMapper.writeValueAsBytes(value == null ? Map.of() : value);
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("learning snapshot digest unavailable", error);
        }
    }

    private String pathItemKey(String title, int order) {
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(slug)) {
            slug = "step";
        }
        return text("python-" + slug + "-" + order, 120);
    }

    private String resourceKindAlias(String kind) {
        return switch (String.valueOf(kind)) {
            case "explanation" -> "knowledge_note";
            case "mind_map", "diagram" -> "mind_map";
            case "exercise" -> "practice_set";
            case "code_example" -> "code_lab";
            case "presentation" -> "presentation";
            case "extended_reading" -> "extended_reading";
            default -> "";
        };
    }

    private int progress(Object value, int fallback) {
        int parsed = value instanceof Number number ? number.intValue() : fallback;
        return Math.max(fallback, Math.min(100, Math.max(0, parsed)));
    }

    private int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeAuthorization(String authorization) {
        return authorization == null ? "" : authorization;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value, int maxLength) {
        String result = value == null ? "" : String.valueOf(value).trim();
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(item -> item instanceof String)
                .map(item -> String.valueOf(item).trim())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(this::mapValue)
                .toList();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(Result.BAD_REQUEST_CODE, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(Result.NOT_FOUND_CODE, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
