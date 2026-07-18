package com.example.appbackend.service.impl;

import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.UserProfileDimension;
import com.example.appbackend.entity.UserProfileEvidence;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserProfileDimensionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final String EVIDENCE_PROTOCOL_VERSION = "campus-profile-evidence-v1";
    private static final String PROFILE_SUMMARY_AGENT = "profile_summary_agent";
    private static final String AGENT_MODEL_BINDING_PREFIX = "ai.agent-bindings.";
    private static final int PROFILE_SUMMARY_CACHE_MAX_ENTRIES = 1_000;
    private static final Duration PROFILE_SUMMARY_CACHE_EXPIRY = Duration.ofHours(6);
    private static final int RECENT_EVIDENCE_DAYS = 30;
    private static final Map<String, Double> SOURCE_RELIABILITY_WEIGHTS = Map.ofEntries(
            Map.entry("profile", 0.90),
            Map.entry("profile_form", 0.90),
            Map.entry("user_profile", 0.90),
            Map.entry("schedule", 0.90),
            Map.entry("course", 0.90),
            Map.entry("grade", 0.90),
            Map.entry("exam", 0.85),
            Map.entry("question_result", 0.85),
            Map.entry("wrong_question", 0.85),
            Map.entry("task", 0.85),
            Map.entry("meeting", 0.75),
            Map.entry("meeting_summary", 0.75),
            Map.entry("member_analysis", 0.75),
            Map.entry("chat", 0.70),
            Map.entry("app_ai_assistant", 0.70),
            Map.entry("user_statement", 0.70),
            Map.entry("click", 0.55),
            Map.entry("resource", 0.55),
            Map.entry("assistant_resource", 0.55),
            Map.entry("favorite", 0.55),
            Map.entry("download", 0.55),
            Map.entry("navigation", 0.55),
            Map.entry("forum", 0.55),
            Map.entry("activity", 0.55),
            Map.entry("leader_route", 0.45),
            Map.entry("ai", 0.40),
            Map.entry("inference", 0.40)
    );

    private static final List<String> GLOBAL_RULES = List.of(
            "行为、聊天、会议、做题和点击会持续实时记录到画像证据池。",
            "雷达图不按单条证据即时改分，而是由定时汇总任务把历史画像和最新证据融合后更新。",
            "正式更新必须有明确来源、证据内容、置信度和建议变化方向。",
            "低置信度证据留在候选池等待更多同类行为；高置信新证据会在最近一次定时汇总中体现。",
            "每次定时汇总按历史置信度和最新证据置信度计算融合权重，避免一句话大幅改分，也允许真实变化及时体现。",
            "每次画像快照都会优先调用 profile_summary_agent 基于最新分数、证据数量和置信度生成优势、欠缺和下一步补证建议；智能体不可直接改分。",
            "当前输入与历史画像冲突时，以当前输入完成本轮回答，同时把冲突作为新证据沉淀。"
    );

    private static final List<String> LEADER_RULES = List.of(
            "Leader 每次回答前读取 profileSnapshot，但不能直接修改画像分数。",
            "高置信度画像可用于推荐资源、调整解释深度和组织回答顺序。",
            "中低置信度画像只能作为倾向，不得武断判断用户能力或偏好。",
            "用户当前问题优先级高于历史画像；画像只做个性化参考。",
            "回答后如发现新的稳定事实或冲突，应提交画像证据，由画像服务按规则慢更新。"
    );

    private static final List<String> EVIDENCE_FLOW = List.of(
            "1. 用户聊天、会议总结、做题结果、资源点击等行为实时写入 profile_evidence。",
            "2. 提交方只负责判断维度、证据、方向和建议变化分；后端统一计算置信度拆解，不直接改雷达图。",
            "3. 定时画像汇总任务按用户和维度拉取候选证据，并读取该维度历史分数与历史置信度。",
            "4. 汇总任务把一段时间内的新证据聚合成一次画像更新，按历史-最新融合权重更新 user_profile_dimension。",
            "5. 已参与汇总的证据标记为 applied；低置信、冲突或信息不足的证据继续留在候选池。",
            "6. 画像快照优先调用 profile_summary_agent 生成智能总结，说明当前强项、欠缺、证据状态和后续补证建议；JSON 不合法时使用后端规则总结兜底。",
            "7. 移动端雷达图和 Leader 统一读取最新画像快照。"
    );

    private static final List<UserProfileDTO.EvidenceScoringCriterion> EVIDENCE_SCORING_CRITERIA = buildEvidenceScoringCriteria();
    private static final List<UserProfileDTO.SourceReliabilityRule> SOURCE_RELIABILITY_RULES = buildSourceReliabilityRules();
    private static final List<UserProfileDTO.ScoreDeltaRule> SCORE_DELTA_RULES = buildScoreDeltaRules();
    private static final List<UserProfileDTO.UpdateDecisionStep> UPDATE_DECISION_STEPS = buildUpdateDecisionSteps();
    private static final List<UserProfileDTO.LeaderUsagePolicy> LEADER_USAGE_POLICIES = buildLeaderUsagePolicies();
    private static final List<UserProfileDTO.ConflictPolicy> CONFLICT_POLICIES = buildConflictPolicies();
    private static final List<UserProfileDTO.EvidenceSubmissionField> EVIDENCE_SUBMISSION_FIELDS = buildEvidenceSubmissionFields();
    private static final List<UserProfileDTO.EvidenceSubmissionExample> EVIDENCE_SUBMISSION_EXAMPLES = buildEvidenceSubmissionExamples();
    private static final List<UserProfileDTO.AutoCaptureSource> AUTO_CAPTURE_SOURCES = buildAutoCaptureSources();
    private static final List<String> EVIDENCE_PROTOCOL_RULES = List.of(
            "证据提交采用 actor-action-object-result-context-time 的事件结构，参考 xAPI/Caliper 的学习行为记录方式。",
            "画像审计采用 entity-activity-agent 的来源追溯结构，参考 W3C PROV 思路保留来源、活动和责任主体。",
            "客户端或智能体可以给 confidence 建议值，但后端会重新计算并生成 confidenceBreakdown。",
            "最终 confidence 由后端计算值和提交方建议值保守融合，避免智能体单次自信导致画像漂移。",
            "所有证据先进入 candidate；雷达图只由定时汇总任务统一更新。"
    );
    private static final List<String> AUDIT_FIELDS = List.of(
            "dimensionKey：证据影响的画像维度",
            "sourceType/sourceId：证据来源与来源业务 ID",
            "action/objectType/objectId/objectName：本次行为动作和被作用对象",
            "occurredAt：行为实际发生时间；缺失时使用提交时间",
            "evidence：原始证据摘要，不保存空泛结论",
            "direction：positive、negative、weakness、increase、decrease 或中文方向词",
            "confidence：0-1 后端融合后的综合置信度",
            "confidenceBreakdown：来源可靠性、表达明确度、重复出现度、时间新鲜度、历史一致性拆解",
            "suggestedDelta：智能体建议变化值，定时汇总时会按融合权重裁剪",
            "status：candidate 表示已记录待汇总，applied 表示已参与画像汇总",
            "reason：等待汇总、冲突、低置信或正式采纳的原因",
            "appliedDelta/applyTime：汇总任务实际应用变化和应用时间"
    );
    private static final List<String> ACCEPTANCE_CRITERIA = List.of(
            "用户行为、聊天、会议、做题和点击会实时记录为画像证据。",
            "单条证据不会即时更新雷达图；画像分数只由定时汇总任务更新。",
            "低于维度最低置信度的证据留在候选池，等待更多同类行为一起判断。",
            "会议、做题或对话中的高置信新证据会在最近一次定时汇总中影响画像，并被融合权重限制幅度。",
            "历史高置信画像不会被一次弱证据推翻；历史低置信画像会更容易吸收强新证据。",
            "Leader 每次可以读取画像并调整回答方式，但不能直接修改画像分数。",
            "画像快照必须返回强项总结、欠缺总结、置信依据、数据状态和补证建议，不能把默认基线伪装成真实画像。",
            "当前用户明确表达与历史画像冲突时，本轮回答必须以当前表达为准。",
            "每次正式分数变化都能追溯到来源、证据、置信度、实际改分和原因。",
            "后台规则页能看到评分公式、来源权重、更新节奏、冲突处理和 Leader 使用边界。"
    );
    private static final Map<String, UserProfileDTO.DimensionRule> RULES = buildRules();

    private final UserProfileDimensionRepository dimensionRepository;
    private final UserProfileEvidenceRepository evidenceRepository;
    private final PythonAiProxyService pythonAiProxyService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final Executor profileSummaryExecutor;
    private final ProfileSummaryInsightCache<CachedProfileSummary> profileSummaryCache =
            new ProfileSummaryInsightCache<>(
                    PROFILE_SUMMARY_CACHE_MAX_ENTRIES,
                    PROFILE_SUMMARY_CACHE_EXPIRY,
                    Clock.systemUTC());
    private final Set<Long> profileSummaryRefreshInFlight = ConcurrentHashMap.newKeySet();

    public UserProfileServiceImpl(UserProfileDimensionRepository dimensionRepository,
                                  UserProfileEvidenceRepository evidenceRepository,
                                  PythonAiProxyService pythonAiProxyService,
                                  SystemConfigService systemConfigService,
                                  ObjectMapper objectMapper,
                                  @Qualifier("profileSummaryExecutor") Executor profileSummaryExecutor) {
        this.dimensionRepository = dimensionRepository;
        this.evidenceRepository = evidenceRepository;
        this.pythonAiProxyService = pythonAiProxyService;
        this.systemConfigService = systemConfigService;
        this.objectMapper = objectMapper;
        this.profileSummaryExecutor = profileSummaryExecutor;
    }

    @Override
    @Transactional
    public UserProfileDTO.RadarSnapshot getSnapshot(Long userId) {
        return getSnapshot(userId, "");
    }

    @Override
    @Transactional
    public UserProfileDTO.RadarSnapshot getSnapshot(Long userId, String authorization) {
        List<UserProfileDTO.DimensionSnapshot> dimensions = RULES.values().stream()
                .map(rule -> toSnapshot(getOrCreateDimension(userId, rule), rule))
                .toList();
        return buildSnapshot(userId, dimensions, authorization);
    }

    @Override
    public UserProfileDTO.AdminRulesResponse getRules() {
        UserProfileDTO.AdminRulesResponse response = new UserProfileDTO.AdminRulesResponse();
        response.setRules(new ArrayList<>(RULES.values()));
        response.setGlobalRules(GLOBAL_RULES);
        response.setLeaderRules(LEADER_RULES);
        response.setEvidenceFlow(EVIDENCE_FLOW);
        response.setEvidenceScoringCriteria(EVIDENCE_SCORING_CRITERIA);
        response.setSourceReliabilityRules(SOURCE_RELIABILITY_RULES);
        response.setScoreDeltaRules(SCORE_DELTA_RULES);
        response.setUpdateDecisionSteps(UPDATE_DECISION_STEPS);
        response.setLeaderUsagePolicies(LEADER_USAGE_POLICIES);
        response.setConflictPolicies(CONFLICT_POLICIES);
        response.setEvidenceSubmissionFields(EVIDENCE_SUBMISSION_FIELDS);
        response.setEvidenceSubmissionExamples(EVIDENCE_SUBMISSION_EXAMPLES);
        response.setAutoCaptureSources(AUTO_CAPTURE_SOURCES);
        response.setEvidenceProtocolRules(EVIDENCE_PROTOCOL_RULES);
        response.setAuditFields(AUDIT_FIELDS);
        response.setAcceptanceCriteria(ACCEPTANCE_CRITERIA);
        return response;
    }

    @Override
    @Transactional
    public UserProfileDTO.EvidenceResponse addEvidence(Long userId, UserProfileDTO.EvidenceRequest request) {
        String dimensionKey = normalize(request.getDimensionKey());
        String sourceType = normalize(request.getSourceType());
        UserProfileDTO.DimensionRule rule = RULES.get(dimensionKey);
        if (rule == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "画像维度不存在：" + request.getDimensionKey());
        }

        int requestedDelta = normalizeDelta(request);
        UserProfileDimension dimension = getOrCreateDimension(userId, rule);
        ConfidenceEvaluation confidenceEvaluation = evaluateConfidence(userId, rule, dimension, request, requestedDelta);
        double confidence = resolveFinalConfidence(request.getConfidence(), confidenceEvaluation.total());
        Map<String, Object> confidenceBreakdown = confidenceEvaluation.toMap(request.getConfidence(), confidence);
        Map<String, Object> metadata = buildEvidenceMetadata(
                userId,
                request,
                rule,
                sourceType,
                requestedDelta,
                confidenceBreakdown
        );

        UserProfileEvidence evidence = new UserProfileEvidence();
        evidence.setUserId(userId);
        evidence.setDimensionKey(dimensionKey);
        evidence.setSourceType(sourceType);
        evidence.setSourceId(StringUtils.hasText(request.getSourceId()) ? request.getSourceId().trim() : "");
        evidence.setAction(resolveAction(request, sourceType));
        evidence.setObjectType(resolveObjectType(request, sourceType));
        evidence.setObjectId(StringUtils.hasText(request.getObjectId()) ? request.getObjectId().trim() : evidence.getSourceId());
        evidence.setObjectName(resolveObjectName(request, rule));
        evidence.setResult(StringUtils.hasText(request.getResult()) ? truncate(request.getResult().trim(), 300) : "");
        evidence.setEvidence(request.getEvidence().trim());
        evidence.setDirection(StringUtils.hasText(request.getDirection()) ? request.getDirection().trim() : "");
        evidence.setConfidence(confidence);
        evidence.setSuggestedDelta(requestedDelta);
        evidence.setMetadataJson(writeJson(metadata));
        evidence.setConfidenceBreakdownJson(writeJson(confidenceBreakdown));
        evidence.setOccurredAt(request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt());
        evidence.setStatus("candidate");
        evidence.setAppliedDelta(0);
        evidence.setReason("证据已记录到画像候选池，等待定时画像汇总任务统一更新");
        evidenceRepository.save(evidence);

        return evidenceResponse(
                dimensionKey,
                "candidate",
                false,
                0,
                evidence.getReason(),
                confidence,
                confidenceBreakdown,
                toSnapshot(dimension, rule)
        );
    }

    @Override
    @Transactional
    public Map<String, Object> buildLeaderProfileContext(Long userId) {
        return buildLeaderProfileContext(userId, "");
    }

    @Override
    @Transactional
    public Map<String, Object> buildLeaderProfileContext(Long userId, String authorization) {
        UserProfileDTO.RadarSnapshot snapshot = getSnapshot(userId, authorization);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("overallScore", snapshot.getOverallScore());
        context.put("confidenceLevel", snapshot.getConfidenceLevel());
        context.put("dataStatus", snapshot.getDataStatus());
        context.put("dataStatusText", snapshot.getDataStatusText());
        context.put("dataSourceText", snapshot.getDataSourceText());
        context.put("profileTags", snapshot.getProfileTags());
        context.put("strongDimensions", snapshot.getStrongDimensions());
        context.put("weakDimensions", snapshot.getWeakDimensions());
        context.put("advantageDimensions", snapshot.getAdvantageDimensions());
        context.put("gapDimensions", snapshot.getGapDimensions());
        context.put("aiSummary", snapshot.getAiSummary());
        context.put("strengthSummary", snapshot.getStrengthSummary());
        context.put("weaknessSummary", snapshot.getWeaknessSummary());
        context.put("improvementSuggestions", snapshot.getImprovementSuggestions());
        context.put("confidenceNotes", snapshot.getConfidenceNotes());
        context.put("summaryEngine", snapshot.getSummaryEngine());
        context.put("resourcePreference", snapshot.getResourcePreference());
        context.put("outputPreferenceHints", buildOutputPreferenceHints(userId));
        context.put("updateMode", snapshot.getUpdateMode());
        context.put("summaryUpdatedAt", snapshot.getSummaryUpdatedAt());
        context.put("lastUpdatedAt", snapshot.getLastUpdatedAt());
        context.put("dimensions", snapshot.getDimensions().stream().map(item -> {
            Map<String, Object> dimension = new LinkedHashMap<>();
            dimension.put("key", item.getKey());
            dimension.put("name", item.getName());
            dimension.put("score", item.getScore());
            dimension.put("confidence", item.getConfidence());
            dimension.put("trend", item.getTrend());
            dimension.put("sourceSummary", item.getSourceSummary());
            dimension.put("updatePolicy", item.getUpdatePolicy());
            return dimension;
        }).toList());
        context.put("leaderUsageRules", LEADER_RULES);
        context.put("updateContract", Map.of(
                "leaderCanUpdateScore", false,
                "leaderCanSubmitEvidence", true,
                "scoreUpdateOwner", "UserProfileService scheduled evidence summarizer",
                "updateMode", "行为证据实时记录，画像分数定时汇总更新",
                "lowConfidencePolicy", "只做倾向，不做确定判断"
        ));
        return context;
    }

    @Override
    public void refreshLeaderProfileContextAsync(Long userId, String authorization) {
        String safeAuthorization = bearerAuthorization(authorization);
        if (userId == null || !StringUtils.hasText(safeAuthorization)
                || !profileSummaryRefreshInFlight.add(userId)) {
            return;
        }
        try {
            profileSummaryExecutor.execute(() -> {
                try {
                    UserProfileDTO.RadarSnapshot snapshot = getSnapshot(userId);
                    if (!PROFILE_SUMMARY_AGENT.equals(snapshot.getSummaryEngine())) {
                        List<UserProfileDTO.DimensionSnapshot> dimensions = snapshot.getDimensions() == null
                                ? List.of() : snapshot.getDimensions();
                        String fingerprint = profileSummaryFingerprint(snapshot, dimensions);
                        applyProfileSummaryAgent(
                                userId, snapshot, dimensions, safeAuthorization, fingerprint);
                    }
                } catch (Exception ignored) {
                    // Background refresh is best-effort. Never expose the authorization or fail the Leader request.
                } finally {
                    profileSummaryRefreshInFlight.remove(userId);
                }
            });
        } catch (RejectedExecutionException | IllegalStateException ignored) {
            profileSummaryRefreshInFlight.remove(userId);
        }
    }

    @Scheduled(fixedDelayString = "${profile.update.fixed-delay-ms:1800000}", initialDelayString = "${profile.update.initial-delay-ms:60000}")
    @Transactional
    public void refreshProfilesFromEvidencePool() {
        List<UserProfileEvidence> candidates = evidenceRepository.findByStatusAndCreateTimeBefore("candidate", LocalDateTime.now());
        if (candidates.isEmpty()) {
            return;
        }
        Map<Long, Map<String, List<UserProfileEvidence>>> grouped = new HashMap<>();
        for (UserProfileEvidence evidence : candidates) {
            if (evidence.getUserId() == null || !StringUtils.hasText(evidence.getDimensionKey())) {
                continue;
            }
            grouped
                    .computeIfAbsent(evidence.getUserId(), ignored -> new HashMap<>())
                    .computeIfAbsent(evidence.getDimensionKey(), ignored -> new ArrayList<>())
                    .add(evidence);
        }
        grouped.forEach((userId, byDimension) ->
                byDimension.forEach((dimensionKey, evidenceList) -> applyEvidenceBatch(userId, dimensionKey, evidenceList))
        );
    }

    private UserProfileDTO.RadarSnapshot buildSnapshot(Long userId,
                                                       List<UserProfileDTO.DimensionSnapshot> dimensions,
                                                       String authorization) {
        UserProfileDTO.RadarSnapshot snapshot = new UserProfileDTO.RadarSnapshot();
        snapshot.setUserId(userId);
        snapshot.setDimensions(dimensions);
        snapshot.setOverallScore((int) Math.round(dimensions.stream().mapToInt(UserProfileDTO.DimensionSnapshot::getScore).average().orElse(70)));
        double confidenceAverage = dimensions.stream().mapToDouble(UserProfileDTO.DimensionSnapshot::getConfidence).average().orElse(0.5);
        snapshot.setConfidenceLevel(confidenceAverage >= 0.75 ? "high" : confidenceAverage >= 0.55 ? "medium" : "low");
        List<String> strongDimensions = dimensions.stream()
                .filter(item -> item.getScore() >= 78)
                .map(UserProfileDTO.DimensionSnapshot::getName)
                .toList();
        List<String> weakDimensions = dimensions.stream()
                .filter(item -> item.getScore() <= 68)
                .map(UserProfileDTO.DimensionSnapshot::getName)
                .toList();
        snapshot.setStrongDimensions(strongDimensions);
        snapshot.setWeakDimensions(weakDimensions);
        snapshot.setAdvantageDimensions(buildAdvantageDimensions(dimensions));
        snapshot.setGapDimensions(buildGapDimensions(dimensions));
        snapshot.setResourcePreference(buildResourcePreference(dimensions));
        snapshot.setProfileTags(buildProfileTags(snapshot.getStrongDimensions(), snapshot.getWeakDimensions()));
        snapshot.setLeaderUsageRules(LEADER_RULES);
        snapshot.setUpdateMode("行为证据实时记录，画像分数定时汇总更新");
        LocalDateTime lastUpdatedAt = dimensions.stream()
                .map(UserProfileDTO.DimensionSnapshot::getLastUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        int appliedEvidenceCount = dimensions.stream()
                .mapToInt(item -> item.getEvidenceCount() == null ? 0 : item.getEvidenceCount())
                .sum();
        int totalEvidenceCount = safeLongToInt(evidenceRepository.countByUserId(userId));
        int candidateEvidenceCount = safeLongToInt(evidenceRepository.countByUserIdAndStatus(userId, "candidate"));
        snapshot.setAppliedEvidenceCount(appliedEvidenceCount);
        snapshot.setTotalEvidenceCount(totalEvidenceCount);
        snapshot.setCandidateEvidenceCount(candidateEvidenceCount);
        if (appliedEvidenceCount > 0) {
            snapshot.setDataStatus("evidence_ready");
            snapshot.setDataStatusText("真实画像");
            snapshot.setDataSourceText("已基于 " + appliedEvidenceCount + " 条正式采纳证据更新；另有 " + candidateEvidenceCount + " 条候选证据等待汇总。");
        } else if (totalEvidenceCount > 0) {
            snapshot.setDataStatus("evidence_collecting");
            snapshot.setDataStatusText("证据沉淀中");
            snapshot.setDataSourceText("已记录 " + totalEvidenceCount + " 条真实行为证据，雷达分仍使用默认基线等待定时汇总。");
        } else {
            snapshot.setDataStatus("baseline");
            snapshot.setDataStatusText("默认基线");
            snapshot.setDataSourceText("当前还没有可采纳的聊天、会议、做题或点击证据，分数来自后端默认画像基线。");
        }
        ProfileInsight insight = buildProfileInsight(snapshot, dimensions, confidenceAverage);
        snapshot.setAiSummary(insight.summary());
        snapshot.setStrengthSummary(insight.strengthSummary());
        snapshot.setWeaknessSummary(insight.weaknessSummary());
        snapshot.setImprovementSuggestions(insight.suggestions());
        snapshot.setConfidenceNotes(insight.confidenceNotes());
        snapshot.setSummaryEngine("local_profile_summary_v1");
        snapshot.setSummaryUpdatedAt(lastUpdatedAt);
        snapshot.setLastUpdatedAt(lastUpdatedAt);
        String fingerprint = profileSummaryFingerprint(snapshot, dimensions);
        applyCachedProfileSummary(userId, snapshot, fingerprint);
        if (StringUtils.hasText(authorization)) {
            applyProfileSummaryAgent(userId, snapshot, dimensions, authorization, fingerprint);
        }
        return snapshot;
    }

    private List<String> buildAdvantageDimensions(List<UserProfileDTO.DimensionSnapshot> dimensions) {
        List<String> advantages = dimensions.stream()
                .filter(item -> item.getScore() >= 75 || "up".equals(normalize(item.getTrend())))
                .sorted(Comparator.comparingInt(UserProfileDTO.DimensionSnapshot::getScore).reversed())
                .limit(3)
                .map(this::dimensionLabel)
                .toList();
        if (!advantages.isEmpty()) {
            return advantages;
        }
        return dimensions.stream()
                .sorted(Comparator.comparingInt(UserProfileDTO.DimensionSnapshot::getScore).reversed())
                .limit(2)
                .map(this::dimensionLabel)
                .toList();
    }

    private List<String> buildGapDimensions(List<UserProfileDTO.DimensionSnapshot> dimensions) {
        List<String> gaps = dimensions.stream()
                .filter(item -> item.getScore() <= 68 || "down".equals(normalize(item.getTrend())))
                .sorted(Comparator.comparingInt(UserProfileDTO.DimensionSnapshot::getScore))
                .limit(3)
                .map(this::dimensionLabel)
                .toList();
        if (!gaps.isEmpty()) {
            return gaps;
        }
        return dimensions.stream()
                .sorted(Comparator.comparingInt(UserProfileDTO.DimensionSnapshot::getScore))
                .limit(2)
                .map(this::dimensionLabel)
                .toList();
    }

    private ProfileInsight buildProfileInsight(UserProfileDTO.RadarSnapshot snapshot,
                                               List<UserProfileDTO.DimensionSnapshot> dimensions,
                                               double confidenceAverage) {
        List<String> advantages = snapshot.getAdvantageDimensions() == null ? List.of() : snapshot.getAdvantageDimensions();
        List<String> gaps = snapshot.getGapDimensions() == null ? List.of() : snapshot.getGapDimensions();
        String confidenceText = switch (snapshot.getConfidenceLevel()) {
            case "high" -> "高置信";
            case "low" -> "低置信";
            default -> "中置信";
        };
        String evidenceText = switch (snapshot.getDataStatus()) {
            case "evidence_ready" -> "已采纳真实证据";
            case "evidence_collecting" -> "真实证据正在沉淀";
            default -> "仍处在默认基线阶段";
        };
        String topAdvantage = advantages.isEmpty() ? "暂未形成稳定优势" : String.join("、", advantages);
        String topGap = gaps.isEmpty() ? "暂未发现明显欠缺" : String.join("、", gaps);
        String summary = "当前画像综合分 " + snapshot.getOverallScore() + "，属于" + confidenceText + "画像，" + evidenceText
                + "。优势主要集中在 " + topAdvantage + "；需要继续观察或补强的是 " + topGap + "。";
        String strengthSummary = advantages.isEmpty()
                ? "优势尚未稳定，需要更多聊天、会议、做题和资源使用证据来确认。"
                : "较有优势：" + topAdvantage + "。Leader 可在这些方向上给更结构化、稍进阶的解释。";
        String weaknessSummary = gaps.isEmpty()
                ? "当前没有明显短板，但仍要用最新问题校正历史画像。"
                : "欠缺或待确认：" + topGap + "。Leader 回答时应优先补基础、给例子，并避免直接贴负面标签。";
        List<String> suggestions = new ArrayList<>();
        if ("baseline".equals(snapshot.getDataStatus())) {
            suggestions.add("先通过 AI 对话、会议总结、做题记录和资源点击积累真实证据，再进行正式画像更新。");
        } else if ("evidence_collecting".equals(snapshot.getDataStatus())) {
            suggestions.add("候选证据已有积累，等待定时汇总任务把多条证据融合到正式雷达分。");
        } else {
            suggestions.add("继续记录最新聊天、会议、练习和资源选择，下一次汇总时对强弱变化做小幅校正。");
        }
        dimensions.stream()
                .filter(item -> item.getScore() <= 68)
                .findFirst()
                .ifPresent(item -> suggestions.add("围绕「" + item.getName() + "」补充更具体的题目、知识点或任务结果，提升判断准确度。"));
        if (confidenceAverage < 0.65) {
            suggestions.add("当前置信度还不够高，Leader 只能把画像作为倾向，回答仍应优先相信用户本轮明确表达。");
        }
        List<String> confidenceNotes = new ArrayList<>();
        confidenceNotes.add("当前平均置信度约 " + round2(confidenceAverage) + "，来自各维度历史置信度的平均值。");
        confidenceNotes.add("已采纳证据 " + snapshot.getAppliedEvidenceCount() + " 条，候选证据 " + snapshot.getCandidateEvidenceCount() + " 条。");
        if ("baseline".equals(snapshot.getDataStatus())) {
            confidenceNotes.add("暂无真实证据时只展示默认基线，不作为确定性结论。");
        }
        return new ProfileInsight(
                summary,
                strengthSummary,
                weaknessSummary,
                suggestions.stream().distinct().limit(3).toList(),
                confidenceNotes.stream().distinct().limit(3).toList()
        );
    }

    @SuppressWarnings("unchecked")
    private void applyProfileSummaryAgent(Long userId,
                                          UserProfileDTO.RadarSnapshot snapshot,
                                          List<UserProfileDTO.DimensionSnapshot> dimensions,
                                          String authorization,
                                          String fingerprint) {
        if (!StringUtils.hasText(authorization)) {
            return;
        }
        String modelBinding = resolveProfileSummaryModelBinding();
        if (!StringUtils.hasText(modelBinding)) {
            return;
        }
        Map<String, Object> profilePayload = buildProfileSummaryPayload(snapshot, dimensions);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("agentName", PROFILE_SUMMARY_AGENT);
        request.put("llmModel", modelBinding);
        request.put("input", writeJson(profilePayload));
        request.put("metadata", Map.of(
                "source", "user_profile_snapshot",
                "profileSnapshot", profilePayload
        ));
        try {
            Object raw = pythonAiProxyService.queryRag(request, authorization);
            Map<String, Object> result = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            String answer = String.valueOf(result.getOrDefault("answer", "")).trim();
            if (applyProfileSummaryAgentJson(snapshot, answer)) {
                snapshot.setSummaryEngine(PROFILE_SUMMARY_AGENT);
                snapshot.setSummaryUpdatedAt(LocalDateTime.now());
                profileSummaryCache.put(userId, CachedProfileSummary.from(snapshot, fingerprint));
            }
        } catch (Exception ignored) {
            // Keep a compatible cached insight (or the local fallback) when the remote agent is unavailable.
        }
    }

    private void applyCachedProfileSummary(Long userId,
                                           UserProfileDTO.RadarSnapshot snapshot,
                                           String fingerprint) {
        CachedProfileSummary cached = profileSummaryCache.get(userId);
        if (cached == null) {
            return;
        }
        if (!Objects.equals(cached.fingerprint(), fingerprint)) {
            profileSummaryCache.remove(userId);
            return;
        }
        cached.applyTo(snapshot);
    }

    private String profileSummaryFingerprint(UserProfileDTO.RadarSnapshot snapshot,
                                             List<UserProfileDTO.DimensionSnapshot> dimensions) {
        String payload = writeJson(buildProfileSummaryPayload(snapshot, dimensions));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String bearerAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return "";
        }
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        String token = value.substring(7).trim();
        if (!StringUtils.hasText(token) || token.chars().anyMatch(Character::isWhitespace)) {
            return "";
        }
        return "Bearer " + token;
    }

    private Map<String, Object> buildProfileSummaryPayload(UserProfileDTO.RadarSnapshot snapshot,
                                                           List<UserProfileDTO.DimensionSnapshot> dimensions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overallScore", snapshot.getOverallScore());
        payload.put("confidenceLevel", snapshot.getConfidenceLevel());
        payload.put("dataStatus", snapshot.getDataStatus());
        payload.put("totalEvidenceCount", snapshot.getTotalEvidenceCount());
        payload.put("appliedEvidenceCount", snapshot.getAppliedEvidenceCount());
        payload.put("candidateEvidenceCount", snapshot.getCandidateEvidenceCount());
        payload.put("strongDimensions", snapshot.getStrongDimensions());
        payload.put("weakDimensions", snapshot.getWeakDimensions());
        payload.put("resourcePreference", snapshot.getResourcePreference());
        payload.put("leaderUsageRules", snapshot.getLeaderUsageRules());
        payload.put("updateMode", snapshot.getUpdateMode());
        payload.put("lastUpdatedAt", snapshot.getLastUpdatedAt());
        payload.put("dimensions", dimensions.stream().map(item -> {
            Map<String, Object> dimension = new LinkedHashMap<>();
            dimension.put("key", item.getKey());
            dimension.put("name", item.getName());
            dimension.put("score", item.getScore());
            dimension.put("confidence", item.getConfidence());
            dimension.put("trend", item.getTrend());
            dimension.put("evidenceCount", item.getEvidenceCount());
            dimension.put("sourceSummary", item.getSourceSummary());
            dimension.put("updatePolicy", item.getUpdatePolicy());
            dimension.put("lastUpdatedAt", item.getLastUpdatedAt());
            return dimension;
        }).toList());
        return payload;
    }

    private boolean applyProfileSummaryAgentJson(UserProfileDTO.RadarSnapshot snapshot, String answer) {
        String json = extractJsonObject(answer);
        if (!StringUtils.hasText(json)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String aiSummary = jsonText(root, "aiSummary");
            String strengthSummary = jsonText(root, "strengthSummary");
            String weaknessSummary = jsonText(root, "weaknessSummary");
            if (!StringUtils.hasText(aiSummary) || !StringUtils.hasText(strengthSummary) || !StringUtils.hasText(weaknessSummary)) {
                return false;
            }
            snapshot.setAiSummary(truncate(aiSummary, 700));
            snapshot.setStrengthSummary(truncate(strengthSummary, 700));
            snapshot.setWeaknessSummary(truncate(weaknessSummary, 700));
            List<String> advantageDimensions = jsonTextList(root, "advantageDimensions", 3);
            List<String> gapDimensions = jsonTextList(root, "gapDimensions", 3);
            List<String> improvementSuggestions = jsonTextList(root, "improvementSuggestions", 4);
            List<String> confidenceNotes = jsonTextList(root, "confidenceNotes", 4);
            if (!advantageDimensions.isEmpty()) {
                snapshot.setAdvantageDimensions(advantageDimensions);
            }
            if (!gapDimensions.isEmpty()) {
                snapshot.setGapDimensions(gapDimensions);
            }
            if (!improvementSuggestions.isEmpty()) {
                snapshot.setImprovementSuggestions(improvementSuggestions);
            }
            if (!confidenceNotes.isEmpty()) {
                snapshot.setConfidenceNotes(confidenceNotes);
            }
            String dataStatusText = jsonText(root, "dataStatusText");
            if (List.of("真实画像", "证据沉淀中", "默认基线").contains(dataStatusText)) {
                snapshot.setDataStatusText(dataStatusText);
            }
            String dataSourceText = jsonText(root, "dataSourceText");
            if (StringUtils.hasText(dataSourceText)) {
                snapshot.setDataSourceText(truncate(dataSourceText, 300));
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String resolveProfileSummaryModelBinding() {
        String profileModel = systemConfigService.getValue(AGENT_MODEL_BINDING_PREFIX + PROFILE_SUMMARY_AGENT + ".model", "");
        if (StringUtils.hasText(profileModel)) {
            return profileModel.trim();
        }
        String leaderModel = systemConfigService.getValue(AGENT_MODEL_BINDING_PREFIX + "leader_agent.model", "");
        return StringUtils.hasText(leaderModel) ? leaderModel.trim() : "";
    }

    private Map<String, Object> buildOutputPreferenceHints(Long userId) {
        List<UserProfileEvidence> recent = evidenceRepository.findByUserIdAndDimensionKeyAndCreateTimeAfter(
                userId,
                "resource_preference",
                LocalDateTime.now().minusDays(90)
        );
        if (recent.isEmpty()) {
            return Map.of(
                    "preferredFormat", "",
                    "confidenceLevel", "low",
                    "usageRule", "没有稳定输出形式偏好时，Leader 应先询问用户要图片、文件还是文本。"
            );
        }

        Map<String, OutputPreferenceAccumulator> accumulators = new LinkedHashMap<>();
        accumulators.put("document", new OutputPreferenceAccumulator("document", "文件/文档"));
        accumulators.put("image", new OutputPreferenceAccumulator("image", "图片/图解"));
        accumulators.put("video", new OutputPreferenceAccumulator("video", "视频"));
        accumulators.put("code", new OutputPreferenceAccumulator("code", "代码案例"));
        accumulators.put("text", new OutputPreferenceAccumulator("text", "文本总结"));

        for (UserProfileEvidence evidence : recent) {
            String signal = String.join(" ",
                    nullToEmpty(evidence.getEvidence()),
                    nullToEmpty(evidence.getObjectName()),
                    nullToEmpty(evidence.getResult()),
                    nullToEmpty(evidence.getMetadataJson())
            ).toLowerCase();
            double confidence = evidence.getConfidence() == null ? 0.5 : evidence.getConfidence();
            int preferencePolarity = outputPreferencePolarity(evidence);
            int preferenceWeight = Math.max(1, Math.min(3,
                    (int) Math.abs((long) (evidence.getSuggestedDelta() == null ? 0 : evidence.getSuggestedDelta()))));
            if (containsAny(signal, "文件", "文档", "word", "docx", "pdf", "ppt", "excel", "表格", "markdown", "md", "下载")) {
                accumulators.get("document").add(confidence, evidence.getEvidence(), preferencePolarity, preferenceWeight);
            }
            if (containsAny(signal, "图片", "图解", "配图", "流程图", "思维导图", "架构图", "海报", "image", "png", "jpg")) {
                accumulators.get("image").add(confidence, evidence.getEvidence(), preferencePolarity, preferenceWeight);
            }
            if (containsAny(signal, "视频", "video", "mp4")) {
                accumulators.get("video").add(confidence, evidence.getEvidence(), preferencePolarity, preferenceWeight);
            }
            if (containsAny(signal, "代码", "code", "示例代码", "案例")) {
                accumulators.get("code").add(confidence, evidence.getEvidence(), preferencePolarity, preferenceWeight);
            }
            if (containsAny(signal, "文本", "总结", "要点", "直接说", "文字")) {
                accumulators.get("text").add(confidence, evidence.getEvidence(), preferencePolarity, preferenceWeight);
            }
        }

        List<Map<String, Object>> formats = accumulators.values().stream()
                .filter(item -> item.count > 0 && item.score() > 0)
                .sorted(Comparator.comparingDouble(OutputPreferenceAccumulator::score).reversed())
                .map(OutputPreferenceAccumulator::toMap)
                .toList();
        if (formats.isEmpty()) {
            return Map.of(
                    "preferredFormat", "",
                    "confidenceLevel", "low",
                    "usageRule", "近期有资源偏好证据，但没有稳定输出形式偏好；需要先询问用户。"
            );
        }

        Map<String, Object> top = formats.get(0);
        double avgConfidence = (double) top.get("averageConfidence");
        int count = (int) top.get("evidenceCount");
        String confidenceLevel = avgConfidence >= 0.76 && count >= 2 ? "high" : avgConfidence >= 0.62 ? "medium" : "low";
        return Map.of(
                "preferredFormat", top.get("format"),
                "preferredFormatLabel", top.get("label"),
                "confidenceLevel", confidenceLevel,
                "formats", formats,
                "usageRule", "高/中置信偏好可作为默认推送形式；回答结尾应轻量提示是否还需要另一种形式。"
        );
    }

    private int outputPreferencePolarity(UserProfileEvidence evidence) {
        int delta = evidence.getSuggestedDelta() == null ? 0 : evidence.getSuggestedDelta();
        if (delta < 0) {
            return -1;
        }
        if (delta > 0) {
            return 1;
        }
        String direction = normalize(evidence.getDirection());
        return containsAny(direction, "weakness", "negative", "decrease", "下降", "薄弱", "退步") ? -1 : 1;
    }

    private List<String> buildResourcePreference(List<UserProfileDTO.DimensionSnapshot> dimensions) {
        int resourceScore = dimensions.stream()
                .filter(item -> "resource_preference".equals(item.getKey()))
                .findFirst()
                .map(UserProfileDTO.DimensionSnapshot::getScore)
                .orElse(70);
        if (resourceScore >= 75) {
            return List.of("图解优先", "代码案例优先", "结构化总结");
        }
        if (resourceScore >= 65) {
            return List.of("图解与文字结合", "偏好仍在观察");
        }
        return List.of("资源偏好证据不足", "需要更多点击与反馈");
    }

    private List<String> buildProfileTags(List<String> strongDimensions, List<String> weakDimensions) {
        List<String> tags = new ArrayList<>();
        if (strongDimensions.contains("专业课程")) {
            tags.add("专业提升型");
        }
        if (strongDimensions.contains("学习目标")) {
            tags.add("目标清晰");
        }
        if (weakDimensions.contains("薄弱知识")) {
            tags.add("薄弱点待巩固");
        }
        if (tags.isEmpty()) {
            tags.add("画像持续观察中");
        }
        return tags;
    }

    private void applyEvidenceBatch(Long userId, String dimensionKey, List<UserProfileEvidence> evidenceList) {
        UserProfileDTO.DimensionRule rule = RULES.get(normalize(dimensionKey));
        if (rule == null || evidenceList == null || evidenceList.isEmpty()) {
            return;
        }
        List<UserProfileEvidence> validEvidence = evidenceList.stream()
                .filter(evidence -> evidence.getConfidence() != null && evidence.getConfidence() >= rule.getMinConfidence())
                .filter(evidence -> evidence.getSuggestedDelta() != null && evidence.getSuggestedDelta() != 0)
                .toList();
        if (validEvidence.isEmpty()) {
            evidenceList.forEach(evidence -> {
                if (!StringUtils.hasText(evidence.getReason()) || evidence.getReason().contains("等待定时")) {
                    evidence.setReason("候选证据已记录，但本轮汇总未达到该维度最低置信度或缺少明确变化方向");
                }
            });
            evidenceRepository.saveAll(evidenceList);
            return;
        }

        double positiveWeight = validEvidence.stream()
                .filter(evidence -> evidence.getSuggestedDelta() > 0)
                .mapToDouble(evidence -> Math.abs(evidence.getSuggestedDelta()) * evidence.getConfidence())
                .sum();
        double negativeWeight = validEvidence.stream()
                .filter(evidence -> evidence.getSuggestedDelta() < 0)
                .mapToDouble(evidence -> Math.abs(evidence.getSuggestedDelta()) * evidence.getConfidence())
                .sum();
        double totalWeight = positiveWeight + negativeWeight;
        if (totalWeight <= 0 || Math.abs(positiveWeight - negativeWeight) / totalWeight < 0.2) {
            validEvidence.forEach(evidence -> evidence.setReason("本轮画像汇总发现正负证据接近，继续留在候选池等待更多行为"));
            evidenceRepository.saveAll(validEvidence);
            return;
        }

        int direction = positiveWeight >= negativeWeight ? 1 : -1;
        double averageConfidence = validEvidence.stream()
                .mapToDouble(UserProfileEvidence::getConfidence)
                .average()
                .orElse(rule.getMinConfidence());
        int requestedDelta = direction * Math.min(
                rule.getSingleUpdateLimit(),
                Math.max(1, (int) Math.round(totalWeight / Math.max(1, validEvidence.size())))
        );

        UserProfileDimension dimension = getOrCreateDimension(userId, rule);
        int appliedDelta = calculateFusionDelta(dimension, rule, averageConfidence, requestedDelta);
        int nextScore = clamp(dimension.getScore() + appliedDelta, 0, 100);
        appliedDelta = nextScore - dimension.getScore();
        if (appliedDelta == 0) {
            validEvidence.forEach(evidence -> evidence.setReason("本轮画像汇总后维度分数已到边界，继续保留候选证据"));
            evidenceRepository.saveAll(validEvidence);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        dimension.setScore(nextScore);
        dimension.setConfidence(round2(Math.min(0.98, Math.max(
                dimension.getConfidence(),
                dimension.getConfidence() * 0.7 + averageConfidence * 0.3
        ))));
        dimension.setTrend(appliedDelta > 0 ? "up" : "down");
        dimension.setEvidenceCount((dimension.getEvidenceCount() == null ? 0 : dimension.getEvidenceCount()) + validEvidence.size());
        List<String> sourceSummary = readStringList(dimension.getSourceSummaryJson());
        for (UserProfileEvidence evidence : validEvidence) {
            sourceSummary = mergeSourceSummary(sourceSummary, evidence.getSourceType());
        }
        dimension.setSourceSummaryJson(writeJson(sourceSummary));
        dimension.setLastUpdatedAt(now);
        dimensionRepository.save(dimension);

        int finalAppliedDelta = appliedDelta;
        validEvidence.forEach(evidence -> {
            evidence.setStatus("applied");
            evidence.setAppliedDelta(finalAppliedDelta);
            evidence.setApplyTime(now);
            evidence.setReason("已参与定时画像汇总，本批次实际变化 " + finalAppliedDelta + " 分");
        });
        evidenceRepository.saveAll(validEvidence);
    }

    private ConfidenceEvaluation evaluateConfidence(Long userId,
                                                    UserProfileDTO.DimensionRule rule,
                                                    UserProfileDimension dimension,
                                                    UserProfileDTO.EvidenceRequest request,
                                                    int requestedDelta) {
        double sourceReliability = sourceReliabilityScore(request.getSourceType());
        double expressionClarity = expressionClarityScore(request, requestedDelta);
        double repetition = repetitionScore(userId, rule, request);
        double recency = recencyScore(request.getOccurredAt());
        double historyConsistency = historyConsistencyScore(dimension, requestedDelta);
        double total = round2(
                sourceReliability * 0.35
                        + expressionClarity * 0.25
                        + repetition * 0.20
                        + recency * 0.10
                        + historyConsistency * 0.10
        );
        List<String> reasons = new ArrayList<>();
        reasons.add("来源可靠性 " + sourceReliability + "，来源类型：" + normalize(request.getSourceType()));
        reasons.add("表达明确度 " + expressionClarity + "，依据：维度、方向、对象和证据内容完整度");
        reasons.add("重复出现度 " + repetition + "，统计最近 " + RECENT_EVIDENCE_DAYS + " 天同维度证据");
        reasons.add("时间新鲜度 " + recency + "，依据 occurredAt 或提交时间");
        reasons.add("历史一致性 " + historyConsistency + "，依据当前画像趋势和历史置信度");
        return new ConfidenceEvaluation(
                sourceReliability,
                expressionClarity,
                repetition,
                recency,
                historyConsistency,
                total,
                reasons
        );
    }

    private double sourceReliabilityScore(String sourceType) {
        String normalized = normalize(sourceType);
        if (!StringUtils.hasText(normalized)) {
            return 0.40;
        }
        if (SOURCE_RELIABILITY_WEIGHTS.containsKey(normalized)) {
            return SOURCE_RELIABILITY_WEIGHTS.get(normalized);
        }
        if (normalized.contains("资料") || normalized.contains("课表") || normalized.contains("成绩") || normalized.contains("用户填写")) {
            return 0.90;
        }
        if (normalized.contains("答题") || normalized.contains("错题") || normalized.contains("任务完成")) {
            return 0.85;
        }
        if (normalized.contains("会议") || normalized.contains("成员分析")) {
            return 0.75;
        }
        if (normalized.contains("聊天") || normalized.contains("对话") || normalized.contains("明确表达")) {
            return 0.70;
        }
        if (normalized.contains("点击") || normalized.contains("浏览") || normalized.contains("收藏") || normalized.contains("下载")) {
            return 0.55;
        }
        return 0.45;
    }

    private double expressionClarityScore(UserProfileDTO.EvidenceRequest request, int requestedDelta) {
        double score = 0.35;
        String evidence = request.getEvidence() == null ? "" : request.getEvidence().trim();
        if (StringUtils.hasText(request.getDirection()) || requestedDelta != 0) {
            score += 0.18;
        }
        if (evidence.length() >= 20) {
            score += 0.18;
        }
        if (evidence.length() >= 60) {
            score += 0.08;
        }
        if (StringUtils.hasText(request.getAction())) {
            score += 0.08;
        }
        if (StringUtils.hasText(request.getObjectName()) || StringUtils.hasText(request.getObjectId())) {
            score += 0.13;
        }
        if (request.getEvidenceTags() != null && !request.getEvidenceTags().isEmpty()) {
            score += 0.08;
        }
        if (containsAny(evidence, "不会", "不懂", "薄弱", "错题", "完成", "通过", "喜欢", "目标", "备考", "项目", "图解", "代码")) {
            score += 0.10;
        }
        return round2(clamp(score, 0.25, 1.0));
    }

    private double repetitionScore(Long userId, UserProfileDTO.DimensionRule rule, UserProfileDTO.EvidenceRequest request) {
        List<UserProfileEvidence> recent = evidenceRepository.findByUserIdAndDimensionKeyAndCreateTimeAfter(
                userId,
                rule.getKey(),
                LocalDateTime.now().minusDays(RECENT_EVIDENCE_DAYS)
        );
        if (recent.isEmpty()) {
            return 0.35;
        }
        String sourceType = normalize(request.getSourceType());
        String sourceId = StringUtils.hasText(request.getSourceId()) ? request.getSourceId().trim() : "";
        String objectId = StringUtils.hasText(request.getObjectId()) ? request.getObjectId().trim() : "";
        long sameSourceType = recent.stream().filter(item -> sourceType.equals(normalize(item.getSourceType()))).count();
        long sameSourceId = recent.stream().filter(item -> StringUtils.hasText(sourceId) && sourceId.equals(item.getSourceId())).count();
        long sameObjectId = recent.stream().filter(item -> StringUtils.hasText(objectId) && objectId.equals(item.getObjectId())).count();
        double score = 0.35
                + Math.min(0.34, recent.size() * 0.07)
                + Math.min(0.18, sameSourceType * 0.06)
                + Math.min(0.08, sameSourceId * 0.04)
                + Math.min(0.05, sameObjectId * 0.05);
        return round2(clamp(score, 0.35, 1.0));
    }

    private double recencyScore(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            return 1.0;
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(occurredAt, LocalDateTime.now()));
        if (days <= 7) {
            return 1.0;
        }
        if (days <= 30) {
            return 0.75;
        }
        if (days <= 90) {
            return 0.55;
        }
        return 0.35;
    }

    private double historyConsistencyScore(UserProfileDimension dimension, int requestedDelta) {
        if (requestedDelta == 0) {
            return 0.58;
        }
        String trend = normalize(dimension.getTrend());
        double historyConfidence = dimension.getConfidence() == null ? 0.5 : dimension.getConfidence();
        if (!StringUtils.hasText(trend) || "stable".equals(trend)) {
            return historyConfidence >= 0.75 ? 0.72 : 0.80;
        }
        boolean sameDirection = (requestedDelta > 0 && "up".equals(trend)) || (requestedDelta < 0 && "down".equals(trend));
        if (sameDirection) {
            return 0.88;
        }
        return historyConfidence >= 0.75 ? 0.42 : 0.60;
    }

    private double resolveFinalConfidence(Double submittedConfidence, double computedConfidence) {
        double computed = round2(clamp(computedConfidence, 0, 1));
        if (submittedConfidence == null) {
            return computed;
        }
        double submitted = clamp(submittedConfidence, 0, 1);
        double blended = computed * 0.75 + submitted * 0.25;
        double min = Math.max(0, computed - 0.12);
        double max = Math.min(1, computed + 0.12);
        return round2(clamp(blended, min, max));
    }

    private Map<String, Object> buildEvidenceMetadata(Long userId,
                                                      UserProfileDTO.EvidenceRequest request,
                                                      UserProfileDTO.DimensionRule rule,
                                                      String sourceType,
                                                      int requestedDelta,
                                                      Map<String, Object> confidenceBreakdown) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.getMetadata() != null) {
            request.getMetadata().forEach((key, value) -> {
                if (StringUtils.hasText(key)) {
                    metadata.put(key, value);
                }
            });
        }

        LocalDateTime occurredAt = request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt();
        String sourceId = StringUtils.hasText(request.getSourceId()) ? request.getSourceId().trim() : "";
        String objectId = StringUtils.hasText(request.getObjectId()) ? request.getObjectId().trim() : sourceId;
        String action = resolveAction(request, sourceType);
        String objectType = resolveObjectType(request, sourceType);
        String objectName = resolveObjectName(request, rule);

        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", objectType);
        object.put("id", objectId);
        object.put("name", objectName);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("dimensionKey", rule.getKey());
        context.put("dimensionName", rule.getName());
        context.put("sourceType", sourceType);
        context.put("sourceId", sourceId);
        context.put("evidenceTags", safeEvidenceTags(request.getEvidenceTags()));
        context.put("suggestedDelta", requestedDelta);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("actor", "user:" + userId);
        event.put("action", action);
        event.put("object", object);
        event.put("result", StringUtils.hasText(request.getResult()) ? request.getResult().trim() : "");
        event.put("context", context);
        event.put("eventTime", occurredAt.toString());

        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("entity", "user_profile_evidence:" + rule.getKey());
        provenance.put("activity", sourceType + ":" + action);
        provenance.put("agent", "smart-campus-profile-service");
        provenance.put("wasGeneratedBy", sourceId);
        provenance.put("generatedAt", LocalDateTime.now().toString());

        metadata.put("protocolVersion", EVIDENCE_PROTOCOL_VERSION);
        metadata.put("event", event);
        metadata.put("provenance", provenance);
        metadata.put("confidenceBreakdown", confidenceBreakdown);
        metadata.put("standardsReference", List.of(
                "xAPI-like actor/verb/object/result/context/timestamp",
                "Caliper-like actor/action/object/eventTime",
                "W3C PROV-like entity/activity/agent provenance"
        ));
        return metadata;
    }

    private String resolveAction(UserProfileDTO.EvidenceRequest request, String sourceType) {
        if (StringUtils.hasText(request.getAction())) {
            return truncate(request.getAction().trim(), 80);
        }
        return switch (normalize(sourceType)) {
            case "chat", "app_ai_assistant", "user_statement" -> "expressed";
            case "meeting", "meeting_summary", "member_analysis" -> "analyzed";
            case "exam", "question_result", "wrong_question" -> "answered";
            case "click", "resource", "assistant_resource", "favorite", "download" -> "interacted";
            case "profile", "profile_form", "user_profile" -> "declared";
            default -> "observed";
        };
    }

    private String resolveObjectType(UserProfileDTO.EvidenceRequest request, String sourceType) {
        if (StringUtils.hasText(request.getObjectType())) {
            return truncate(request.getObjectType().trim(), 80);
        }
        return switch (normalize(sourceType)) {
            case "chat", "app_ai_assistant", "user_statement", "leader_route" -> "conversation";
            case "meeting", "meeting_summary", "member_analysis" -> "meeting";
            case "exam", "question_result", "wrong_question" -> "question";
            case "click", "resource", "assistant_resource", "favorite", "download" -> "resource";
            case "schedule", "course", "grade" -> "course";
            default -> "profile_evidence";
        };
    }

    private String resolveObjectName(UserProfileDTO.EvidenceRequest request, UserProfileDTO.DimensionRule rule) {
        if (StringUtils.hasText(request.getObjectName())) {
            return truncate(request.getObjectName().trim(), 200);
        }
        List<String> tags = safeEvidenceTags(request.getEvidenceTags());
        if (!tags.isEmpty()) {
            return truncate(String.join("、", tags), 200);
        }
        return rule.getName();
    }

    private List<String> safeEvidenceTags(List<String> evidenceTags) {
        if (evidenceTags == null) {
            return List.of();
        }
        return evidenceTags.stream()
                .filter(StringUtils::hasText)
                .map(item -> truncate(item.trim(), 40))
                .distinct()
                .limit(12)
                .toList();
    }

    private boolean containsAny(String text, String... tokens) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int calculateFusionDelta(UserProfileDimension dimension,
                                     UserProfileDTO.DimensionRule rule,
                                     double latestConfidence,
                                     int requestedDelta) {
        int direction = requestedDelta > 0 ? 1 : -1;
        int boundedDelta = Math.min(Math.abs(requestedDelta), rule.getSingleUpdateLimit());
        double historyConfidence = dimension.getConfidence() == null ? rule.getDefaultConfidence() : dimension.getConfidence();
        double latestWeight = latestConfidence / Math.max(0.01, historyConfidence + latestConfidence);
        double urgencyBoost = latestConfidence >= 0.85 ? 0.35 : latestConfidence >= 0.75 ? 0.2 : 0.1;
        double fusionWeight = clamp(latestWeight + urgencyBoost, 0.35, 1.0);
        int appliedDelta = (int) Math.round(boundedDelta * fusionWeight);
        return direction * Math.max(1, appliedDelta);
    }

    private UserProfileDimension getOrCreateDimension(Long userId, UserProfileDTO.DimensionRule rule) {
        return dimensionRepository.findByUserIdAndDimensionKey(userId, rule.getKey())
                .orElseGet(() -> {
                    UserProfileDimension dimension = new UserProfileDimension();
                    dimension.setUserId(userId);
                    dimension.setDimensionKey(rule.getKey());
                    dimension.setName(rule.getName());
                    dimension.setShortName(rule.getShortName());
                    dimension.setDescription(rule.getDescription());
                    dimension.setScore(rule.getDefaultScore());
                    dimension.setConfidence(rule.getDefaultConfidence());
                    dimension.setTrend("stable");
                    dimension.setEvidenceCount(0);
                    dimension.setUpdatePolicy(rule.getUpdatePolicy());
                    dimension.setSourceSummaryJson(writeJson(rule.getSourceTypes().stream().limit(3).toList()));
                    return dimensionRepository.save(dimension);
                });
    }

    private UserProfileDTO.DimensionSnapshot toSnapshot(UserProfileDimension dimension, UserProfileDTO.DimensionRule rule) {
        UserProfileDTO.DimensionSnapshot snapshot = new UserProfileDTO.DimensionSnapshot();
        snapshot.setKey(rule.getKey());
        snapshot.setName(rule.getName());
        snapshot.setShortName(rule.getShortName());
        snapshot.setDescription(rule.getDescription());
        snapshot.setScore(dimension.getScore());
        snapshot.setConfidence(round2(dimension.getConfidence()));
        snapshot.setTrend(dimension.getTrend());
        snapshot.setEvidenceCount(dimension.getEvidenceCount());
        snapshot.setSourceSummary(readStringList(dimension.getSourceSummaryJson()));
        snapshot.setUpdatePolicy(rule.getUpdatePolicy());
        snapshot.setLastUpdatedAt(dimension.getLastUpdatedAt());
        return snapshot;
    }

    private UserProfileDTO.EvidenceResponse evidenceResponse(String dimensionKey,
                                                             String status,
                                                             boolean accepted,
                                                             int appliedDelta,
                                                             String reason,
                                                             double confidence,
                                                             Map<String, Object> confidenceBreakdown,
                                                             UserProfileDTO.DimensionSnapshot snapshot) {
        UserProfileDTO.EvidenceResponse response = new UserProfileDTO.EvidenceResponse();
        response.setDimensionKey(dimensionKey);
        response.setStatus(status);
        response.setAccepted(accepted);
        response.setAppliedDelta(appliedDelta);
        response.setReason(reason);
        response.setConfidence(confidence);
        response.setConfidenceBreakdown(confidenceBreakdown);
        response.setSnapshot(snapshot);
        return response;
    }

    private int normalizeDelta(UserProfileDTO.EvidenceRequest request) {
        String direction = normalize(request.getDirection());
        int delta = request.getSuggestedDelta() == null ? 0 : request.getSuggestedDelta();
        if (direction.contains("weakness") || direction.contains("negative") || direction.contains("decrease")
                || direction.contains("下降") || direction.contains("薄弱") || direction.contains("退步")) {
            return -Math.max(1, Math.abs(delta));
        }
        if (direction.contains("positive") || direction.contains("increase") || direction.contains("improve")
                || direction.contains("上升") || direction.contains("增强") || direction.contains("提升")) {
            return Math.max(1, Math.abs(delta));
        }
        return clamp(delta, -10, 10);
    }

    private List<String> mergeSourceSummary(List<String> current, String sourceType) {
        List<String> merged = new ArrayList<>(current);
        String label = sourceLabel(sourceType);
        if (!merged.contains(label)) {
            merged.add(0, label);
        }
        return merged.stream().filter(StringUtils::hasText).limit(5).collect(Collectors.toList());
    }

    private String sourceLabel(String sourceType) {
        return switch (normalize(sourceType)) {
            case "chat" -> "AI 对话";
            case "meeting" -> "会议总结";
            case "exam" -> "做题记录";
            case "click" -> "资源点击";
            case "assistant_resource" -> "助手资源互动";
            case "profile" -> "用户资料";
            default -> StringUtils.hasText(sourceType) ? sourceType : "未知来源";
        };
    }

    private String dimensionLabel(UserProfileDTO.DimensionSnapshot item) {
        if (item == null) {
            return "";
        }
        String trend = switch (normalize(item.getTrend())) {
            case "up" -> "，上升";
            case "down" -> "，下降";
            default -> "";
        };
        return item.getName() + " " + item.getScore() + "分" + trend;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String jsonText(JsonNode root, String field) {
        if (root == null || !root.has(field)) {
            return "";
        }
        return truncate(root.path(field).asText("").trim(), 1000);
    }

    private List<String> jsonTextList(JsonNode root, String field, int limit) {
        if (root == null || !root.has(field) || !root.path(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : root.path(field)) {
            String value = truncate(node.asText("").trim(), 200);
            if (StringUtils.hasText(value) && !values.contains(value)) {
                values.add(value);
            }
            if (values.size() >= limit) {
                break;
            }
        }
        return values;
    }

    private String extractJsonObject(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return value.substring(start, end + 1);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int safeLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static List<UserProfileDTO.EvidenceScoringCriterion> buildEvidenceScoringCriteria() {
        return List.of(
                evidenceCriterion("source_reliability", "来源可靠性", 35,
                        "判断证据本身来自事实数据、明确表达还是弱行为信号。",
                        List.of("用户资料、课表、成绩、答题记录等可验证事实", "用户在当前会话中明确表达目标或偏好"),
                        List.of("单次浏览、短暂停留、AI 猜测", "来源 ID 缺失或上下文不完整")),
                evidenceCriterion("expression_clarity", "表达明确度", 25,
                        "判断证据是否明确指出维度、方向和具体内容。",
                        List.of("出现明确知识点、课程、偏好或任务结果", "用户直接说喜欢、不会、已完成、正在准备"),
                        List.of("只出现情绪词或泛泛表述", "没有可定位的知识点或行为对象")),
                evidenceCriterion("repetition", "重复出现度", 20,
                        "判断同类证据是否在一段时间内多次出现。",
                        List.of("同一知识点反复提问或错题集中", "连续点击/收藏同类资源"),
                        List.of("孤立的一次提问、一次点击或一次失误")),
                evidenceCriterion("recency", "时间新鲜度", 10,
                        "越新的证据越适合影响短中期画像，长期稳定维度仍需谨慎。",
                        List.of("最近 7 天内的学习行为或任务结果", "本次会话、当前会议、最新题库结果"),
                        List.of("很久以前的行为且没有持续证据", "与当前场景明显过期")),
                evidenceCriterion("history_consistency", "与历史一致性", 10,
                        "判断新证据是否与已有画像趋势一致，冲突时先沉淀候选。",
                        List.of("与已有强维度、弱维度或偏好连续一致", "能解释已有趋势变化"),
                        List.of("与高置信画像明显冲突", "只凭一次证据试图推翻稳定画像"))
        );
    }

    private static List<UserProfileDTO.SourceReliabilityRule> buildSourceReliabilityRules() {
        return List.of(
                sourceRule("用户资料/课表/成绩/明确填写", 0.90, "高", "实时记录，定时汇总时优先影响稳定事实类维度", "用户明确填写专业，或课表出现新专业课程"),
                sourceRule("答题结果/错题记录/任务完成记录", 0.85, "高", "实时记录，定时汇总时影响薄弱知识、学习进度、能力表现", "同一知识点连续错题，或练习正确率稳定提升"),
                sourceRule("会议任务/会议总结/成员分析", 0.75, "中高", "实时记录，会议结束后的最近汇总可影响目标、进度、薄弱点", "会议分配了明确学习任务，成员分析指出理解偏差"),
                sourceRule("用户聊天中的明确表达", 0.70, "中", "实时记录，定时汇总时作为目标、偏好、薄弱点证据", "用户明确说要备考、喜欢图解、某知识点不会"),
                sourceRule("资源点击/浏览/收藏", 0.55, "中低", "实时记录，通常需要连续行为或多来源一致后汇总更新", "连续收藏图解资源，或多次打开代码案例"),
                sourceRule("AI 单次推断/含糊表达", 0.40, "低", "不得直接改分，只能沉淀候选证据", "AI 根据一句话猜测用户偏好或能力")
        );
    }

    private static List<UserProfileDTO.ScoreDeltaRule> buildScoreDeltaRules() {
        return List.of(
                deltaRule("weak", "弱证据", 1,
                        "进入候选池，定时汇总时通常只产生 1 分左右变化。",
                        "单次点击、一次含糊表达、一次轻微错误默认不直接改分。"),
                deltaRule("medium", "中等证据", 2,
                        "通过置信度校验后，在定时汇总中按历史-最新融合权重更新。",
                        "明确表达目标、多次同类点击、一次可验证任务完成。"),
                deltaRule("strong", "强证据", 3,
                        "最新置信度较高时可在最近一次定时汇总中明显影响画像，历史高置信会降低推翻幅度。",
                        "连续错题集中在同一知识点，或多来源均指向同一变化。"),
                deltaRule("multi_source", "多来源连续证据", 3,
                        "定时汇总时按用户和维度聚合处理，每批都保留证据链和实际 appliedDelta。",
                        "后台必须能追溯每次融合应用记录。")
        );
    }

    private static List<UserProfileDTO.UpdateDecisionStep> buildUpdateDecisionSteps() {
        return List.of(
                decisionStep(1, "实时记录证据", "dimensionKey、sourceType、evidence、confidence 存在且合法", "写入候选池并记录缺失字段原因"),
                decisionStep(2, "定时拉取候选池", "汇总任务按用户和维度拉取 candidate 证据", "没有候选证据则跳过"),
                decisionStep(3, "维度与方向过滤", "证据能映射到 7 个维度之一，且 suggestedDelta 非 0", "继续留在候选池等待更多信息"),
                decisionStep(4, "置信度过滤", "confidence 大于等于该维度 minConfidence", "继续留在候选池，不更新雷达图"),
                decisionStep(5, "正负冲突判断", "同一批证据正负方向不接近抵消", "标记冲突原因并留在候选池"),
                decisionStep(6, "历史画像读取", "读取该维度当前 score、confidence、trend 和来源摘要", "缺失时使用默认画像基线"),
                decisionStep(7, "批量融合裁剪", "候选证据聚合成一次 requestedDelta，再按历史-最新融合权重生成 appliedDelta", "实际变化为 0 时继续候选"),
                decisionStep(8, "正式应用与审计", "实际 appliedDelta 非 0 且分数仍在 0-100", "保存 applied 证据、更新趋势和来源摘要"),
                decisionStep(9, "智能总结生成", "读取最新分数、证据状态和置信度", "如果无真实证据则明确标记默认基线，不输出确定性结论")
        );
    }

    private static List<UserProfileDTO.LeaderUsagePolicy> buildLeaderUsagePolicies() {
        return List.of(
                leaderPolicy("高置信强项", "提高解释密度，增加进阶例子或挑战题", "断言用户已经完全掌握", "可以说“我先给你一个进阶版本，再补关键提醒”"),
                leaderPolicy("高置信弱项", "先补基础、多给例子、降低跳步程度", "贴负面标签或让用户感到被评价", "可以说“这个点我多展开一步，方便你对齐概念”"),
                leaderPolicy("资源偏好", "调整图解、代码、视频、文字总结的推荐顺序", "永久固定一种资源形式", "可以说“我先按你最近更常用的图解方式整理”"),
                leaderPolicy("学习目标", "让回答贴近备考、项目、作业或补弱目标", "忽略用户当前问题或强行套目标", "可以说“如果你是为了备考，我会把考点也标出来”"),
                leaderPolicy("学习进度", "控制下一步建议难度和任务量", "因为长期未更新就自动扣分", "可以说“先给你下一步最小可执行任务”"),
                leaderPolicy("中低置信画像", "只作为倾向，混合推荐或轻量提示", "做确定判断或强推荐", "可以说“我先给两个形式，你选更顺手的”")
        );
    }

    private static List<UserProfileDTO.ConflictPolicy> buildConflictPolicies() {
        return List.of(
                conflictPolicy("当前输入与资源偏好冲突", "当前输入优先", "把冲突作为候选证据沉淀，不立刻改偏好", "按用户本次要求输出"),
                conflictPolicy("一次表现推翻长期画像", "长期高置信画像不被一次证据推翻", "进入候选池，等待重复证据", "route_reason 中说明出现新倾向但不武断判断"),
                conflictPolicy("多来源证据互相冲突", "暂停改分，保留原分数", "全部进入候选池并标记 conflict", "回答中避免下结论"),
                conflictPolicy("用户明确纠正画像", "用户明确反馈优先级高", "作为高置信候选证据进入最近一次画像汇总", "承认纠正并按新表达完成本轮回答"),
                conflictPolicy("负向证据缺少具体对象", "不得更新薄弱知识或能力表现", "进入候选池，要求后续证据包含具体知识点/任务", "不输出负面标签")
        );
    }

    private static List<UserProfileDTO.EvidenceSubmissionField> buildEvidenceSubmissionFields() {
        return List.of(
                submissionField("dimensionKey", true, "string", "profile dimension",
                        "画像维度枚举，只能取后台规则中的 7 个 key。", "weak_points"),
                submissionField("sourceType", true, "string", "PROV agent / Caliper edApp",
                        "证据来源类型，用于计算来源可靠性。", "chat / meeting / exam / click / profile"),
                submissionField("sourceId", false, "string", "PROV activity id",
                        "来源业务 ID，用于追溯会话、会议、题目、资源。", "app-ai-xxx 或 meeting-xxx"),
                submissionField("action", false, "string", "xAPI verb / Caliper action",
                        "用户或系统发生了什么行为；缺失时后端按 sourceType 自动补。", "expressed / answered / analyzed / interacted"),
                submissionField("objectType", false, "string", "xAPI object / Caliper object",
                        "行为作用对象类型；缺失时后端按 sourceType 自动补。", "conversation / meeting / question / resource"),
                submissionField("objectId", false, "string", "object id",
                        "行为对象 ID，便于统计同一对象重复证据。", "question-1024"),
                submissionField("objectName", false, "string", "object name",
                        "行为对象名称或知识点名称，表达明确度会参考它。", "循环队列判满条件"),
                submissionField("result", false, "string", "xAPI result",
                        "行为结果摘要，例如正确率、完成状态、会议分析结论。", "连续 3 次答错"),
                submissionField("occurredAt", false, "datetime", "Caliper eventTime / xAPI timestamp",
                        "行为实际发生时间，缺失时使用提交时间。", "2026-06-27T15:30:00"),
                submissionField("evidence", true, "string", "evidence entity",
                        "原始证据摘要，必须具体，不能只写“用户可能薄弱”。", "用户连续询问循环队列 front/rear 判满条件"),
                submissionField("direction", false, "string", "profile delta direction",
                        "变化方向；positive/increase 表示增强，negative/weakness/decrease 表示下降或薄弱。", "weakness"),
                submissionField("suggestedDelta", false, "integer", "profile delta hint",
                        "提交方建议变化分；后端定时汇总会按融合权重裁剪。", "-2"),
                submissionField("confidence", false, "number", "confidence hint",
                        "提交方建议置信度；后端仍会重新计算并保守融合。", "0.72"),
                submissionField("evidenceTags", false, "array<string>", "context tags",
                        "知识点、资源类型、任务标签，用于表达明确度和后台筛选。", "[\"循环队列\", \"判满条件\"]"),
                submissionField("metadata", false, "object", "PROV attributes / extensions",
                        "扩展上下文；后端会追加 event、provenance、confidenceBreakdown。", "{\"agentName\":\"leader_agent\"}")
        );
    }

    private static List<UserProfileDTO.EvidenceSubmissionExample> buildEvidenceSubmissionExamples() {
        Map<String, Object> chatPayload = new LinkedHashMap<>();
        chatPayload.put("dimensionKey", "weak_points");
        chatPayload.put("sourceType", "chat");
        chatPayload.put("sourceId", "app-ai-1782549317335-zgris0");
        chatPayload.put("action", "expressed");
        chatPayload.put("objectType", "conversation");
        chatPayload.put("objectName", "循环队列判满条件");
        chatPayload.put("evidence", "用户明确说循环队列 front/rear 判满条件不懂，并继续追问例子。");
        chatPayload.put("direction", "weakness");
        chatPayload.put("suggestedDelta", -2);
        chatPayload.put("evidenceTags", List.of("循环队列", "front/rear", "判满条件"));

        Map<String, Object> meetingPayload = new LinkedHashMap<>();
        meetingPayload.put("dimensionKey", "learning_progress");
        meetingPayload.put("sourceType", "meeting");
        meetingPayload.put("sourceId", "meeting-xxx:meeting_summary_agent");
        meetingPayload.put("action", "analyzed");
        meetingPayload.put("objectType", "meeting");
        meetingPayload.put("objectName", "数据结构复习会议");
        meetingPayload.put("evidence", "会议纪要明确记录用户已完成栈与队列知识点初稿，并领取下一步练习题整理任务。");
        meetingPayload.put("direction", "increase");
        meetingPayload.put("suggestedDelta", 2);
        meetingPayload.put("evidenceTags", List.of("会议纪要", "任务完成", "学习进度"));

        Map<String, Object> examPayload = new LinkedHashMap<>();
        examPayload.put("dimensionKey", "ability_performance");
        examPayload.put("sourceType", "exam");
        examPayload.put("sourceId", "practice-20260627");
        examPayload.put("action", "answered");
        examPayload.put("objectType", "question_set");
        examPayload.put("objectName", "栈与队列专项练习");
        examPayload.put("result", "正确率 86%，连续两次提升");
        examPayload.put("evidence", "用户在栈与队列专项练习中正确率达到 86%，较上次提升 14 个百分点。");
        examPayload.put("direction", "increase");
        examPayload.put("suggestedDelta", 2);
        examPayload.put("evidenceTags", List.of("栈与队列", "正确率提升"));

        return List.of(
                submissionExample("聊天中暴露薄弱点", "chat", "用户明确表达不会、卡住、想换解释方式时提交。", chatPayload),
                submissionExample("会议后形成学习进度", "meeting", "会议总结、成员分析、资源推荐智能体输出后提交。", meetingPayload),
                submissionExample("练习结果形成能力证据", "exam", "答题、错题、正确率和任务完成记录产生后提交。", examPayload)
        );
    }

    private static List<UserProfileDTO.AutoCaptureSource> buildAutoCaptureSources() {
        return List.of(
                autoCaptureSource("chat", "用户明确说不会/喜欢/要备考/要项目，或在图片版、文件版、文档版、图解版之间做选择。",
                        "AppAiLeaderController", List.of("learning_goal", "resource_preference", "weak_points"),
                        "基础权重 0.70，再按表达明确度、重复度、新鲜度、历史一致性融合。", "只捕捉明确表达，不从普通闲聊强行推断。"),
                autoCaptureSource("meeting", "会议结束自动整理，或手动运行会议总结/成员分析/资源推荐智能体。",
                        "MeetingServiceImpl", List.of("learning_progress", "weak_points", "resource_preference"),
                        "基础权重 0.75，会议智能体结果越具体、对象越明确，表达明确度越高。", "会议证据先进入候选池，避免一次会议直接改画像。"),
                autoCaptureSource("exam", "练习提交、错题归因、题库练习结果产生。",
                        "Exam/Practice module", List.of("weak_points", "ability_performance", "learning_progress"),
                        "基础权重 0.85，连续正确率/错题趋势会提高重复出现度。", "当前只完成协议和后端能力，后续接入真实答题记录。"),
                autoCaptureSource("click", "资源点击、收藏、下载、打开同类内容多次出现。",
                        "Resource interaction module", List.of("campus_behavior", "resource_preference"),
                        "基础权重 0.55，需要重复出现或多来源一致才容易通过汇总。", "单次点击只做弱候选证据。"),
                autoCaptureSource("profile", "用户资料、课表、成绩、明确填写发生变化。",
                        "Profile/Course module", List.of("course_background", "learning_goal"),
                        "基础权重 0.90，事实类来源优先，但仍通过定时汇总写入雷达图。", "聊天猜测不能替代用户资料。")
        );
    }

    private static Map<String, UserProfileDTO.DimensionRule> buildRules() {
        Map<String, UserProfileDTO.DimensionRule> rules = new LinkedHashMap<>();
        addRule(rules, "campus_behavior", "校园行为", "校园行为",
                "导航、餐饮、优惠、论坛、活动报名、内容浏览和互动行为。",
                List.of("资源点击", "活动报名", "论坛互动", "地图导航", "优惠浏览"),
                List.of("连续浏览同类活动", "多次收藏同类优惠", "经常查看同一校园设施"),
                "以历史行为为基线，最新点击和报名行为按置信度融合；单个弱行为通常只进入候选。",
                "slow", 0.75, 2,
                "用于判断校园服务推荐顺序，不直接推断学习能力。",
                List.of("单次点击不更新正式画像", "需要行为聚合或连续证据", "异常点击只进入候选池"),
                76, 0.62);
        addRule(rules, "course_background", "专业课程", "专业课程",
                "来自用户资料、课表、课程记录和专业背景信息。",
                List.of("用户资料", "课表", "课程记录", "成绩记录"),
                List.of("用户明确填写专业", "课表新增专业课程", "成绩接口出现课程记录"),
                "以历史专业背景为稳定基线；最新资料、课表或课程事实变化会进入最近一次定时汇总。",
                "stable", 0.85, 1,
                "用于选择解释案例和课程语境，不根据低置信度内容改专业判断。",
                List.of("专业/课程事实必须来源明确", "聊天猜测不直接更新", "冲突时保留旧值并等待更多证据"),
                82, 0.7);
        addRule(rules, "learning_goal", "学习目标", "学习目标",
                "通过用户明确表达、AI 对话、会议任务和资源生成主题判断。",
                List.of("AI 对话", "会议任务", "题库生成", "资料生成"),
                List.of("用户说要准备考试", "会议分配了学习任务", "多次生成同一方向资料"),
                "历史目标和最新表达共同决定；会议中出现明确新目标时会进入最近一次定时汇总。",
                "medium", 0.7, 2,
                "用于调整回答目标，例如备考、项目、作业或补弱。",
                List.of("含糊表达只进候选池", "明确目标可以小幅更新", "短期目标不能覆盖长期目标"),
                78, 0.64);
        addRule(rules, "resource_preference", "资源偏好", "资源偏好",
                "根据图解、视频、代码案例、文字总结等资源点击、选择和反馈持续构建。",
                List.of("资源点击", "收藏下载", "AI 对话", "反馈行为"),
                List.of("连续选择代码案例", "收藏图解资源", "明确说喜欢视频讲解"),
                "历史偏好为基线；最新明确反馈优先级高，普通点击按弱证据融合。",
                "slow", 0.72, 2,
                "用于选择资源呈现形式，高置信偏好优先，中低置信只混合推荐。",
                List.of("一次点击不代表偏好", "至少多来源或多次行为", "负反馈优先进入候选池复核"),
                72, 0.58);
        addRule(rules, "weak_points", "薄弱知识", "薄弱知识",
                "来自错题、反复提问、AI 对话中不会的点和会议后的个人总结。",
                List.of("错题记录", "AI 对话", "会议总结", "成员分析"),
                List.of("同一知识点反复提问", "错题集中在同一概念", "会议总结指出理解偏差"),
                "最新错题、会议表现和用户表达权重较高；掌握趋势回升也要能及时融合。",
                "faster", 0.65, 3,
                "用于先补基础和多给示例，但不要贴负面标签。",
                List.of("必须有具体知识点", "不能只凭沉默判断薄弱", "当前已掌握时要允许趋势回升"),
                64, 0.6);
        addRule(rules, "learning_progress", "学习进度", "学习进度",
                "基于会议待办、练习完成、章节学习记录和阶段汇报更新。",
                List.of("会议待办", "练习完成", "章节记录", "任务状态"),
                List.of("完成会议分配任务", "练习提交增加", "章节学习完成"),
                "以历史任务进度为基线，最新会议待办和练习完成记录会进入最近一次定时汇总。",
                "medium", 0.68, 3,
                "用于控制回答节奏和下一步建议，避免推荐过多超前内容。",
                List.of("只记录可验证进展", "口头计划不等于已完成", "长期未更新不自动扣分"),
                68, 0.56);
        addRule(rules, "ability_performance", "能力表现", "能力表现",
                "结合答题正确率、会议参与、任务推进速度和完成质量评估。",
                List.of("答题结果", "会议参与", "任务质量", "阶段产出"),
                List.of("答题正确率稳定提升", "会议发言有完整解释", "任务按时高质量完成"),
                "历史能力表现为基线，最新高质量表现或连续错误按置信度融合，避免一次偶然表现定性。",
                "slow", 0.75, 2,
                "用于决定解释深度和挑战难度，不用于绝对评价用户能力。",
                List.of("不能凭单次表现大幅更新", "质量判断必须有产出依据", "负向判断需要多条证据"),
                74, 0.6);
        return rules;
    }

    private static void addRule(Map<String, UserProfileDTO.DimensionRule> rules,
                                String key,
                                String name,
                                String shortName,
                                String description,
                                List<String> sourceTypes,
                                List<String> evidenceExamples,
                                String updateStrategy,
                                String updatePolicy,
                                double minConfidence,
                                int singleUpdateLimit,
                                String leaderUsage,
                                List<String> validationRules,
                                int defaultScore,
                                double defaultConfidence) {
        UserProfileDTO.DimensionRule rule = new UserProfileDTO.DimensionRule();
        rule.setKey(key);
        rule.setName(name);
        rule.setShortName(shortName);
        rule.setDescription(description);
        rule.setSourceTypes(sourceTypes);
        rule.setEvidenceExamples(evidenceExamples);
        rule.setUpdateStrategy(updateStrategy);
        rule.setUpdatePolicy(updatePolicy);
        rule.setMinConfidence(minConfidence);
        rule.setSingleUpdateLimit(singleUpdateLimit);
        rule.setLeaderUsage(leaderUsage);
        rule.setValidationRules(validationRules);
        rule.setDefaultScore(defaultScore);
        rule.setDefaultConfidence(defaultConfidence);
        rules.put(key, rule);
    }

    private static UserProfileDTO.EvidenceScoringCriterion evidenceCriterion(String key,
                                                                             String name,
                                                                             int weight,
                                                                             String description,
                                                                             List<String> highScoreSignals,
                                                                             List<String> lowScoreSignals) {
        UserProfileDTO.EvidenceScoringCriterion criterion = new UserProfileDTO.EvidenceScoringCriterion();
        criterion.setKey(key);
        criterion.setName(name);
        criterion.setWeight(weight);
        criterion.setDescription(description);
        criterion.setHighScoreSignals(highScoreSignals);
        criterion.setLowScoreSignals(lowScoreSignals);
        return criterion;
    }

    private static UserProfileDTO.SourceReliabilityRule sourceRule(String sourceType,
                                                                   double weight,
                                                                   String reliability,
                                                                   String updatePermission,
                                                                   String example) {
        UserProfileDTO.SourceReliabilityRule rule = new UserProfileDTO.SourceReliabilityRule();
        rule.setSourceType(sourceType);
        rule.setWeight(weight);
        rule.setReliability(reliability);
        rule.setUpdatePermission(updatePermission);
        rule.setExample(example);
        return rule;
    }

    private static UserProfileDTO.ScoreDeltaRule deltaRule(String level,
                                                           String evidenceStrength,
                                                           int suggestedDelta,
                                                           String applyRule,
                                                           String reviewRule) {
        UserProfileDTO.ScoreDeltaRule rule = new UserProfileDTO.ScoreDeltaRule();
        rule.setLevel(level);
        rule.setEvidenceStrength(evidenceStrength);
        rule.setSuggestedDelta(suggestedDelta);
        rule.setApplyRule(applyRule);
        rule.setReviewRule(reviewRule);
        return rule;
    }

    private static UserProfileDTO.UpdateDecisionStep decisionStep(int step,
                                                                  String name,
                                                                  String passCondition,
                                                                  String failAction) {
        UserProfileDTO.UpdateDecisionStep decisionStep = new UserProfileDTO.UpdateDecisionStep();
        decisionStep.setStep(step);
        decisionStep.setName(name);
        decisionStep.setPassCondition(passCondition);
        decisionStep.setFailAction(failAction);
        return decisionStep;
    }

    private static UserProfileDTO.LeaderUsagePolicy leaderPolicy(String profileSignal,
                                                                 String allowedUse,
                                                                 String forbiddenUse,
                                                                 String responseStyle) {
        UserProfileDTO.LeaderUsagePolicy policy = new UserProfileDTO.LeaderUsagePolicy();
        policy.setProfileSignal(profileSignal);
        policy.setAllowedUse(allowedUse);
        policy.setForbiddenUse(forbiddenUse);
        policy.setResponseStyle(responseStyle);
        return policy;
    }

    private static UserProfileDTO.ConflictPolicy conflictPolicy(String scenario,
                                                               String decisionRule,
                                                               String evidenceAction,
                                                               String leaderBehavior) {
        UserProfileDTO.ConflictPolicy policy = new UserProfileDTO.ConflictPolicy();
        policy.setScenario(scenario);
        policy.setDecisionRule(decisionRule);
        policy.setEvidenceAction(evidenceAction);
        policy.setLeaderBehavior(leaderBehavior);
        return policy;
    }

    private static UserProfileDTO.EvidenceSubmissionField submissionField(String field,
                                                                          boolean required,
                                                                          String type,
                                                                          String sourceStandard,
                                                                          String description,
                                                                          String example) {
        UserProfileDTO.EvidenceSubmissionField submissionField = new UserProfileDTO.EvidenceSubmissionField();
        submissionField.setField(field);
        submissionField.setRequired(required);
        submissionField.setType(type);
        submissionField.setSourceStandard(sourceStandard);
        submissionField.setDescription(description);
        submissionField.setExample(example);
        return submissionField;
    }

    private static UserProfileDTO.EvidenceSubmissionExample submissionExample(String scenario,
                                                                              String sourceType,
                                                                              String description,
                                                                              Map<String, Object> payload) {
        UserProfileDTO.EvidenceSubmissionExample example = new UserProfileDTO.EvidenceSubmissionExample();
        example.setScenario(scenario);
        example.setSourceType(sourceType);
        example.setDescription(description);
        example.setPayload(payload);
        return example;
    }

    private static UserProfileDTO.AutoCaptureSource autoCaptureSource(String sourceType,
                                                                      String trigger,
                                                                      String submitter,
                                                                      List<String> dimensions,
                                                                      String confidenceRule,
                                                                      String note) {
        UserProfileDTO.AutoCaptureSource source = new UserProfileDTO.AutoCaptureSource();
        source.setSourceType(sourceType);
        source.setTrigger(trigger);
        source.setSubmitter(submitter);
        source.setDimensions(dimensions);
        source.setConfidenceRule(confidenceRule);
        source.setNote(note);
        return source;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record ConfidenceEvaluation(
            double sourceReliability,
            double expressionClarity,
            double repetition,
            double recency,
            double historyConsistency,
            double total,
            List<String> reasons
    ) {
        Map<String, Object> toMap(Double submittedConfidence, double finalConfidence) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sourceReliability", sourceReliability);
            map.put("expressionClarity", expressionClarity);
            map.put("repetition", repetition);
            map.put("recency", recency);
            map.put("historyConsistency", historyConsistency);
            map.put("computedConfidence", total);
            map.put("submittedConfidence", submittedConfidence);
            map.put("finalConfidence", finalConfidence);
            map.put("formula", "sourceReliability*35% + expressionClarity*25% + repetition*20% + recency*10% + historyConsistency*10%");
            map.put("reasons", reasons);
            return map;
        }
    }

    private record ProfileInsight(
            String summary,
            String strengthSummary,
            String weaknessSummary,
            List<String> suggestions,
            List<String> confidenceNotes
    ) {
    }

    private record CachedProfileSummary(
            String fingerprint,
            String aiSummary,
            String strengthSummary,
            String weaknessSummary,
            List<String> advantageDimensions,
            List<String> gapDimensions,
            List<String> improvementSuggestions,
            List<String> confidenceNotes,
            String dataStatusText,
            String dataSourceText,
            LocalDateTime updatedAt
    ) {
        private static CachedProfileSummary from(UserProfileDTO.RadarSnapshot snapshot, String fingerprint) {
            return new CachedProfileSummary(
                    fingerprint,
                    snapshot.getAiSummary(),
                    snapshot.getStrengthSummary(),
                    snapshot.getWeaknessSummary(),
                    immutableList(snapshot.getAdvantageDimensions()),
                    immutableList(snapshot.getGapDimensions()),
                    immutableList(snapshot.getImprovementSuggestions()),
                    immutableList(snapshot.getConfidenceNotes()),
                    snapshot.getDataStatusText(),
                    snapshot.getDataSourceText(),
                    snapshot.getSummaryUpdatedAt()
            );
        }

        private void applyTo(UserProfileDTO.RadarSnapshot snapshot) {
            snapshot.setAiSummary(aiSummary);
            snapshot.setStrengthSummary(strengthSummary);
            snapshot.setWeaknessSummary(weaknessSummary);
            snapshot.setAdvantageDimensions(advantageDimensions);
            snapshot.setGapDimensions(gapDimensions);
            snapshot.setImprovementSuggestions(improvementSuggestions);
            snapshot.setConfidenceNotes(confidenceNotes);
            snapshot.setDataStatusText(dataStatusText);
            snapshot.setDataSourceText(dataSourceText);
            snapshot.setSummaryEngine(PROFILE_SUMMARY_AGENT);
            snapshot.setSummaryUpdatedAt(updatedAt);
        }

        private static List<String> immutableList(List<String> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    private static class OutputPreferenceAccumulator {
        private final String format;
        private final String label;
        private int count;
        private int negativeCount;
        private double confidenceSum;
        private double netScore;
        private final List<String> examples = new ArrayList<>();

        private OutputPreferenceAccumulator(String format, String label) {
            this.format = format;
            this.label = label;
        }

        private void add(double confidence, String evidence, int polarity, int weight) {
            double boundedConfidence = Math.max(0, Math.min(1, confidence));
            netScore += polarity * boundedConfidence * Math.max(1, weight);
            if (polarity < 0) {
                negativeCount += 1;
                return;
            }
            count += 1;
            confidenceSum += boundedConfidence;
            if (StringUtils.hasText(evidence) && examples.size() < 3) {
                examples.add(truncate(evidence.trim(), 90));
            }
        }

        private double score() {
            return netScore;
        }

        private double averageConfidence() {
            return count <= 0 ? 0 : round2(confidenceSum / count);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("format", format);
            map.put("label", label);
            map.put("evidenceCount", count);
            map.put("negativeEvidenceCount", negativeCount);
            map.put("averageConfidence", averageConfidence());
            map.put("netScore", round2(netScore));
            map.put("examples", examples);
            return map;
        }
    }
}
