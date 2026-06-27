package com.example.appbackend.service.impl;

import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.UserProfileDimension;
import com.example.appbackend.entity.UserProfileEvidence;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserProfileDimensionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final List<String> GLOBAL_RULES = List.of(
            "行为、聊天、会议、做题和点击会持续实时记录到画像证据池。",
            "雷达图不按单条证据即时改分，而是由定时汇总任务把历史画像和最新证据融合后更新。",
            "正式更新必须有明确来源、证据内容、置信度和建议变化方向。",
            "低置信度证据留在候选池等待更多同类行为；高置信新证据会在最近一次定时汇总中体现。",
            "每次定时汇总按历史置信度和最新证据置信度计算融合权重，避免一句话大幅改分，也允许真实变化及时体现。",
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
            "2. 画像提取逻辑只负责判断维度、证据、方向、置信度和建议变化分，不直接改雷达图。",
            "3. 定时画像汇总任务按用户和维度拉取候选证据，并读取该维度历史分数与历史置信度。",
            "4. 汇总任务把一段时间内的新证据聚合成一次画像更新，按历史-最新融合权重更新 user_profile_dimension。",
            "5. 已参与汇总的证据标记为 applied；低置信、冲突或信息不足的证据继续留在候选池。",
            "6. 移动端雷达图和 Leader 统一读取最新画像快照。"
    );

    private static final List<UserProfileDTO.EvidenceScoringCriterion> EVIDENCE_SCORING_CRITERIA = buildEvidenceScoringCriteria();
    private static final List<UserProfileDTO.SourceReliabilityRule> SOURCE_RELIABILITY_RULES = buildSourceReliabilityRules();
    private static final List<UserProfileDTO.ScoreDeltaRule> SCORE_DELTA_RULES = buildScoreDeltaRules();
    private static final List<UserProfileDTO.UpdateDecisionStep> UPDATE_DECISION_STEPS = buildUpdateDecisionSteps();
    private static final List<UserProfileDTO.LeaderUsagePolicy> LEADER_USAGE_POLICIES = buildLeaderUsagePolicies();
    private static final List<UserProfileDTO.ConflictPolicy> CONFLICT_POLICIES = buildConflictPolicies();
    private static final List<String> AUDIT_FIELDS = List.of(
            "dimensionKey：证据影响的画像维度",
            "sourceType/sourceId：证据来源与来源业务 ID",
            "evidence：原始证据摘要，不保存空泛结论",
            "direction：positive、negative、weakness、increase、decrease 或中文方向词",
            "confidence：0-1 综合置信度",
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
            "当前用户明确表达与历史画像冲突时，本轮回答必须以当前表达为准。",
            "每次正式分数变化都能追溯到来源、证据、置信度、实际改分和原因。",
            "后台规则页能看到评分公式、来源权重、更新节奏、冲突处理和 Leader 使用边界。"
    );
    private static final Map<String, UserProfileDTO.DimensionRule> RULES = buildRules();

    private final UserProfileDimensionRepository dimensionRepository;
    private final UserProfileEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;

    public UserProfileServiceImpl(UserProfileDimensionRepository dimensionRepository,
                                  UserProfileEvidenceRepository evidenceRepository,
                                  ObjectMapper objectMapper) {
        this.dimensionRepository = dimensionRepository;
        this.evidenceRepository = evidenceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UserProfileDTO.RadarSnapshot getSnapshot(Long userId) {
        List<UserProfileDTO.DimensionSnapshot> dimensions = RULES.values().stream()
                .map(rule -> toSnapshot(getOrCreateDimension(userId, rule), rule))
                .toList();
        return buildSnapshot(userId, dimensions);
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
        response.setAuditFields(AUDIT_FIELDS);
        response.setAcceptanceCriteria(ACCEPTANCE_CRITERIA);
        return response;
    }

    @Override
    @Transactional
    public UserProfileDTO.EvidenceResponse addEvidence(Long userId, UserProfileDTO.EvidenceRequest request) {
        String dimensionKey = normalize(request.getDimensionKey());
        UserProfileDTO.DimensionRule rule = RULES.get(dimensionKey);
        if (rule == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "画像维度不存在：" + request.getDimensionKey());
        }

        double confidence = round2(clamp(request.getConfidence() == null ? 0.5 : request.getConfidence(), 0, 1));
        int requestedDelta = normalizeDelta(request);
        UserProfileDimension dimension = getOrCreateDimension(userId, rule);

        UserProfileEvidence evidence = new UserProfileEvidence();
        evidence.setUserId(userId);
        evidence.setDimensionKey(dimensionKey);
        evidence.setSourceType(normalize(request.getSourceType()));
        evidence.setSourceId(StringUtils.hasText(request.getSourceId()) ? request.getSourceId().trim() : "");
        evidence.setEvidence(request.getEvidence().trim());
        evidence.setDirection(StringUtils.hasText(request.getDirection()) ? request.getDirection().trim() : "");
        evidence.setConfidence(confidence);
        evidence.setSuggestedDelta(requestedDelta);
        evidence.setMetadataJson(writeJson(request.getMetadata()));
        evidence.setStatus("candidate");
        evidence.setAppliedDelta(0);
        evidence.setReason("证据已记录到画像候选池，等待定时画像汇总任务统一更新");
        evidenceRepository.save(evidence);

        return evidenceResponse(dimensionKey, "candidate", false, 0, evidence.getReason(), toSnapshot(dimension, rule));
    }

    @Override
    @Transactional
    public Map<String, Object> buildLeaderProfileContext(Long userId) {
        UserProfileDTO.RadarSnapshot snapshot = getSnapshot(userId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("overallScore", snapshot.getOverallScore());
        context.put("confidenceLevel", snapshot.getConfidenceLevel());
        context.put("profileTags", snapshot.getProfileTags());
        context.put("strongDimensions", snapshot.getStrongDimensions());
        context.put("weakDimensions", snapshot.getWeakDimensions());
        context.put("resourcePreference", snapshot.getResourcePreference());
        context.put("updateMode", snapshot.getUpdateMode());
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

    private UserProfileDTO.RadarSnapshot buildSnapshot(Long userId, List<UserProfileDTO.DimensionSnapshot> dimensions) {
        UserProfileDTO.RadarSnapshot snapshot = new UserProfileDTO.RadarSnapshot();
        snapshot.setUserId(userId);
        snapshot.setDimensions(dimensions);
        snapshot.setOverallScore((int) Math.round(dimensions.stream().mapToInt(UserProfileDTO.DimensionSnapshot::getScore).average().orElse(70)));
        double confidenceAverage = dimensions.stream().mapToDouble(UserProfileDTO.DimensionSnapshot::getConfidence).average().orElse(0.5);
        snapshot.setConfidenceLevel(confidenceAverage >= 0.75 ? "high" : confidenceAverage >= 0.55 ? "medium" : "low");
        snapshot.setStrongDimensions(dimensions.stream()
                .filter(item -> item.getScore() >= 78)
                .map(UserProfileDTO.DimensionSnapshot::getName)
                .toList());
        snapshot.setWeakDimensions(dimensions.stream()
                .filter(item -> item.getScore() <= 68)
                .map(UserProfileDTO.DimensionSnapshot::getName)
                .toList());
        snapshot.setResourcePreference(buildResourcePreference(dimensions));
        snapshot.setProfileTags(buildProfileTags(snapshot.getStrongDimensions(), snapshot.getWeakDimensions()));
        snapshot.setLeaderUsageRules(LEADER_RULES);
        snapshot.setUpdateMode("行为证据实时记录，画像分数定时汇总更新");
        snapshot.setLastUpdatedAt(dimensions.stream()
                .map(UserProfileDTO.DimensionSnapshot::getLastUpdatedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        return snapshot;
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

    private String candidateReason(UserProfileDTO.DimensionRule rule,
                                   UserProfileDimension dimension,
                                   double confidence,
                                   int requestedDelta,
                                   LocalDateTime now) {
        if (confidence < rule.getMinConfidence()) {
            return "证据置信度低于 " + rule.getMinConfidence() + "，进入候选池";
        }
        if (requestedDelta == 0) {
            return "证据没有明确变化方向，进入候选池";
        }
        return null;
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
                                                             UserProfileDTO.DimensionSnapshot snapshot) {
        UserProfileDTO.EvidenceResponse response = new UserProfileDTO.EvidenceResponse();
        response.setDimensionKey(dimensionKey);
        response.setStatus(status);
        response.setAccepted(accepted);
        response.setAppliedDelta(appliedDelta);
        response.setReason(reason);
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
            case "profile" -> "用户资料";
            default -> StringUtils.hasText(sourceType) ? sourceType : "未知来源";
        };
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
                decisionStep(8, "正式应用与审计", "实际 appliedDelta 非 0 且分数仍在 0-100", "保存 applied 证据、更新趋势和来源摘要")
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
}
