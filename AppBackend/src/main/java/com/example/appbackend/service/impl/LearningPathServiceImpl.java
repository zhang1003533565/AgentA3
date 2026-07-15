package com.example.appbackend.service.impl;

import com.example.appbackend.domain.LearningStatuses;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.entity.LearningKnowledgeMastery;
import com.example.appbackend.entity.LearningPath;
import com.example.appbackend.entity.LearningPathItem;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.LearningKnowledgeMasteryRepository;
import com.example.appbackend.repository.LearningPathItemRepository;
import com.example.appbackend.repository.LearningPathRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.LearningPathService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class LearningPathServiceImpl implements LearningPathService {
    private static final String PYTHON = "python";

    private final LearningPathRepository pathRepository;
    private final LearningPathItemRepository itemRepository;
    private final LearningKnowledgeMasteryRepository masteryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public LearningPathServiceImpl(LearningPathRepository pathRepository,
                                   LearningPathItemRepository itemRepository,
                                   LearningKnowledgeMasteryRepository masteryRepository,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper) {
        this.pathRepository = pathRepository;
        this.itemRepository = itemRepository;
        this.masteryRepository = masteryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public LearningPathDTO.HomeView getHome(Long userId, String courseKey) {
        validateUserAndCourse(userId, courseKey);
        LearningPathDTO.HomeView home = new LearningPathDTO.HomeView();
        home.setUserId(userId);
        home.setCourseKey(courseKey);
        home.setActivePath(getActivePath(userId, courseKey));
        home.setMastery(masteryRepository
                .findByUserIdAndCourseKeyOrderByKnowledgePointKeyAsc(userId, courseKey)
                .stream()
                .map(this::toMasteryView)
                .toList());
        return home;
    }

    @Override
    @Transactional
    public LearningPathDTO.HomeView getHomeForFeedback(Long userId, String courseKey) {
        validateUserAndCourse(userId, courseKey);
        lockUser(userId);
        LearningPathDTO.HomeView home = new LearningPathDTO.HomeView();
        home.setUserId(userId);
        home.setCourseKey(courseKey);
        home.setActivePath(pathRepository.findActiveForUpdate(
                        userId, courseKey, "active")
                .map(this::toPathViewForUpdate)
                .orElse(null));
        home.setMastery(masteryRepository.findByUserIdAndCourseKeyForUpdate(
                        userId, courseKey)
                .stream()
                .map(this::toMasteryView)
                .toList());
        return home;
    }

    @Override
    @Transactional(readOnly = true)
    public LearningPathDTO.PathView getActivePath(Long userId, String courseKey) {
        validateUserAndCourse(userId, courseKey);
        return pathRepository.findByUserIdAndCourseKeyAndStatus(userId, courseKey, "active")
                .map(this::toPathView)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public LearningPathDTO.PathView getPathSnapshot(
            Long userId, Long pathId, Integer version, Long sourceMessageId) {
        validateUserAndCourse(userId, PYTHON);
        if (pathId == null || version == null || sourceMessageId == null) {
            throw badRequest("学习路径快照标识无效");
        }
        LearningPath path = pathRepository.findByIdAndUserIdAndCourseKey(
                        pathId, userId, PYTHON)
                .filter(value -> version.equals(value.getVersionNo())
                        && sourceMessageId.equals(value.getSourceMessageId()))
                .orElseThrow(() -> new BusinessException(
                        Result.NOT_FOUND_CODE, "学习路径快照不存在"));
        return toPathView(path);
    }

    @Override
    public void validatePathDraft(Long userId, LearningPathDTO.PathDraft draft) {
        validateUserAndCourse(userId, draft == null ? null : draft.getCourseKey());
        validateDraft(draft);
    }

    @Override
    @Transactional
    public LearningPathDTO.PathView replaceActivePath(
            Long userId, LearningPathDTO.PathDraft draft) {
        validatePathDraft(userId, draft);
        lockUser(userId);

        List<LearningPath> latestRows = pathRepository.findLatestForUpdate(
                userId, draft.getCourseKey(), PageRequest.of(0, 1));
        LearningPath latest = latestRows.isEmpty() ? null : latestRows.getFirst();
        int nextVersion = latest == null ? 1 : latest.getVersionNo() + 1;
        pathRepository.findActiveForUpdate(
                        userId, draft.getCourseKey(), "active")
                .ifPresent(active -> {
                    active.setStatus("archived");
                    pathRepository.save(active);
                });

        LearningPath path = new LearningPath();
        path.setUserId(userId);
        path.setCourseKey(draft.getCourseKey());
        path.setGoal(draft.getGoal().trim());
        path.setVersionNo(nextVersion);
        path.setStatus("active");
        path.setProfileDigest(draft.getProfileDigest().trim());
        path.setMasteryDigest(draft.getMasteryDigest().trim());
        path.setSourceMessageId(draft.getSourceMessageId());
        path.setGeneratedAt(draft.getGeneratedAt() == null
                ? LocalDateTime.now() : draft.getGeneratedAt());
        path.setNextReplanAt(draft.getNextReplanAt());
        path = pathRepository.save(path);
        if (path.getId() == null) {
            throw new IllegalStateException("Learning path id was not generated");
        }

        List<LearningPathItem> items = new ArrayList<>();
        for (LearningPathDTO.PathItemDraft source : draft.getItems()) {
            LearningPathItem item = new LearningPathItem();
            item.setPathId(path.getId());
            item.setItemKey(source.getItemKey().trim());
            item.setKnowledgePoint(source.getKnowledgePoint().trim());
            item.setObjective(source.getObjective().trim());
            item.setTargetMastery(source.getTargetMastery());
            item.setPriority(source.getPriority());
            item.setSequenceNo(source.getSequenceNo());
            item.setResourceKindsJson(canonicalJson(source.getResourceKinds()));
            item.setResourceIdsJson(canonicalJson(source.getResourceIds()));
            item.setStatus(source.getStatus() == null ? "locked" : source.getStatus());
            item.setDeliveryStatus(source.getDeliveryStatus() == null
                    ? "pending" : source.getDeliveryStatus());
            item.setSourceMessageId(source.getSourceMessageId());
            item.setScheduledAt(source.getScheduledAt());
            item.setRationale(source.getRationale());
            items.add(item);
        }
        itemRepository.saveAll(items);
        return toPathView(path);
    }

    @Override
    @Transactional
    public LearningPathDTO.PathView appendResourcesToPath(
            Long userId,
            Long pathId,
            Integer expectedVersion,
            Long expectedSourceMessageId,
            List<String> resourceIds,
            Long sourceMessageId) {
        validateUserAndCourse(userId, PYTHON);
        if (pathId == null || expectedVersion == null || expectedSourceMessageId == null
                || sourceMessageId == null || resourceIds == null || resourceIds.isEmpty()) {
            throw badRequest("学习路径资源更新参数无效");
        }
        lockUser(userId);
        LearningPath path = pathRepository.findOwnedByIdForUpdate(pathId, userId, PYTHON)
                .filter(value -> expectedVersion.equals(value.getVersionNo())
                        && Objects.equals(expectedSourceMessageId, value.getSourceMessageId()))
                .orElseThrow(() -> new BusinessException(
                        Result.NOT_FOUND_CODE, "学习路径快照不存在"));
        List<LearningPathItem> pathItems = itemRepository.findByPathIdForUpdate(pathId);
        if (pathItems.isEmpty()) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "学习路径节点不存在");
        }
        for (LearningPathItem item : pathItems) {
            List<String> mergedIds = new ArrayList<>(readStringList(item.getResourceIdsJson()));
            mergedIds.addAll(resourceIds);
            item.setResourceIdsJson(canonicalJson(mergedIds));
            item.setDeliveryStatus("available");
            item.setSourceMessageId(sourceMessageId);
        }
        itemRepository.saveAll(pathItems);
        return toPathView(path, pathItems);
    }

    @Override
    @Transactional
    public LearningPathDTO.PathItemView recordResourceInteraction(
            Long userId, Long itemId, LearningPathDTO.InteractionRequest request) {
        String action = request == null ? null : request.getAction();
        if (userId == null || itemId == null
                || !Set.of("view", "open", "complete", "dismiss").contains(action)) {
            throw badRequest("学习资源互动参数无效");
        }
        LearningPathItem item = itemRepository.findOwnedActiveByIdForUpdate(
                        itemId, userId, PYTHON, "active")
                .orElseThrow(() -> new BusinessException(
                        Result.NOT_FOUND_CODE, "学习路径节点不存在"));
        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case "view" -> {
                item.setDeliveryStatus("viewed");
                item.setDeliveredAt(now);
            }
            case "open" -> {
                if (!"completed".equals(item.getStatus())) {
                    item.setStatus("in_progress");
                }
                item.setDeliveryStatus("opened");
                item.setDeliveredAt(now);
            }
            case "dismiss" -> item.setDeliveryStatus("dismissed");
            case "complete" -> {
                item.setStatus("completed");
                item.setDeliveryStatus("completed");
                item.setDeliveredAt(now);
                item.setCompletedAt(now);
            }
            default -> throw new IllegalStateException("Unreachable interaction action");
        }
        return toPathItemView(itemRepository.save(item));
    }

    @Override
    @Transactional
    public LearningPathDTO.MasteryView applyAssessment(
            LearningPathDTO.AssessmentObservation observation) {
        if (observation == null) {
            throw badRequest("掌握度观察不能为空");
        }
        validateUserAndCourse(observation.getUserId(), observation.getCourseKey());
        if (observation.getAttemptId() == null
                || !StringUtils.hasText(observation.getKnowledgePointKey())
                || observation.getCorrect() == null) {
            throw badRequest("掌握度观察参数无效");
        }
        String knowledgePointKey = observation.getKnowledgePointKey().trim();
        lockUser(observation.getUserId());

        LearningKnowledgeMastery mastery = masteryRepository
                .findOneForUpdate(
                        observation.getUserId(), observation.getCourseKey(),
                        knowledgePointKey)
                .orElseGet(() -> newMastery(observation, knowledgePointKey));
        TreeSet<Long> appliedAttemptIds = readAppliedAttemptIds(mastery);
        if (!appliedAttemptIds.add(observation.getAttemptId())) {
            String canonicalReceipts = canonicalAttemptIds(appliedAttemptIds);
            if (!canonicalReceipts.equals(mastery.getAppliedAttemptIdsJson())) {
                mastery.setAppliedAttemptIdsJson(canonicalReceipts);
                mastery = masteryRepository.save(mastery);
            }
            return toMasteryView(mastery);
        }

        int attempts = value(mastery.getAttemptCount()) + 1;
        boolean correct = Boolean.TRUE.equals(observation.getCorrect());
        mastery.setLastAttemptId(observation.getAttemptId());
        mastery.setAppliedAttemptIdsJson(canonicalAttemptIds(appliedAttemptIds));
        mastery.setAttemptCount(attempts);
        mastery.setCorrectCount(value(mastery.getCorrectCount()) + (correct ? 1 : 0));
        mastery.setWrongCount(value(mastery.getWrongCount()) + (correct ? 0 : 1));
        mastery.setScore(nextScore(
                mastery.getScore() == null ? BigDecimal.ZERO : mastery.getScore(),
                correct,
                normalizeDifficulty(observation.getDifficulty())));
        mastery.setStatus(statusFor(mastery.getScore(), attempts));
        mastery.setNextReviewAt(nextReviewAt(mastery.getStatus(), LocalDateTime.now()));
        if (StringUtils.hasText(observation.getKnowledgePointName())) {
            mastery.setKnowledgePointName(observation.getKnowledgePointName().trim());
        }
        return toMasteryView(masteryRepository.save(mastery));
    }

    private LearningKnowledgeMastery newMastery(
            LearningPathDTO.AssessmentObservation observation,
            String knowledgePointKey) {
        LearningKnowledgeMastery mastery = new LearningKnowledgeMastery();
        mastery.setUserId(observation.getUserId());
        mastery.setCourseKey(observation.getCourseKey());
        mastery.setKnowledgePointKey(knowledgePointKey);
        mastery.setKnowledgePointName(StringUtils.hasText(observation.getKnowledgePointName())
                ? observation.getKnowledgePointName().trim() : null);
        mastery.setAttemptCount(0);
        mastery.setCorrectCount(0);
        mastery.setWrongCount(0);
        mastery.setAppliedAttemptIdsJson("[]");
        mastery.setScore(BigDecimal.ZERO.setScale(2));
        mastery.setConfidence(BigDecimal.ZERO.setScale(4));
        mastery.setStatus("new");
        return mastery;
    }

    private BigDecimal nextScore(BigDecimal previous, boolean correct, String difficulty) {
        BigDecimal weight = switch (difficulty) {
            case "hard" -> new BigDecimal("0.40");
            case "medium" -> new BigDecimal("0.30");
            default -> new BigDecimal("0.20");
        };
        BigDecimal signal = correct ? new BigDecimal("100") : BigDecimal.ZERO;
        return previous.multiply(BigDecimal.ONE.subtract(weight))
                .add(signal.multiply(weight))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String statusFor(BigDecimal score, int attempts) {
        if (score.compareTo(new BigDecimal("60")) < 0) return "weak";
        if (score.compareTo(new BigDecimal("80")) < 0) return "learning";
        return attempts >= 3 ? "mastered" : "new";
    }

    private LocalDateTime nextReviewAt(String status, LocalDateTime now) {
        return switch (status) {
            case "weak" -> now.plusDays(1);
            case "learning" -> now.plusDays(3);
            case "mastered" -> now.plusDays(7);
            default -> null;
        };
    }

    private void validateDraft(LearningPathDTO.PathDraft draft) {
        if (draft == null
                || !StringUtils.hasText(draft.getGoal())
                || !StringUtils.hasText(draft.getProfileDigest())
                || !StringUtils.hasText(draft.getMasteryDigest())
                || draft.getItems() == null
                || draft.getItems().isEmpty()) {
            throw badRequest("学习路径草案参数无效");
        }
        Set<String> itemKeys = new HashSet<>();
        Set<Integer> sequenceNumbers = new HashSet<>();
        for (LearningPathDTO.PathItemDraft item : draft.getItems()) {
            if (item == null
                    || !StringUtils.hasText(item.getItemKey())
                    || !StringUtils.hasText(item.getKnowledgePoint())
                    || !StringUtils.hasText(item.getObjective())
                    || item.getTargetMastery() == null
                    || item.getPriority() == null
                    || item.getSequenceNo() == null
                    || !itemKeys.add(item.getItemKey().trim())
                    || !sequenceNumbers.add(item.getSequenceNo())
                    || (item.getStatus() != null
                    && !LearningStatuses.ITEM.contains(item.getStatus()))) {
                throw badRequest("学习路径节点参数无效或标识重复");
            }
        }
    }

    private void validateUserAndCourse(Long userId, String courseKey) {
        if (userId == null || !PYTHON.equals(courseKey)) {
            throw badRequest("仅支持已认证用户的 Python 学习路径");
        }
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(
                        Result.NOT_FOUND_CODE, "用户不存在"));
    }

    private String canonicalJson(List<String> values) {
        List<String> canonical = values == null ? List.of() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize learning path resources", error);
        }
    }

    private LearningPathDTO.PathView toPathView(LearningPath path) {
        return toPathView(path, itemRepository.findByPathIdOrderBySequenceNoAscIdAsc(
                path.getId()));
    }

    private LearningPathDTO.PathView toPathViewForUpdate(LearningPath path) {
        return toPathView(path, itemRepository.findByPathIdForUpdate(path.getId()));
    }

    private LearningPathDTO.PathView toPathView(
            LearningPath path, List<LearningPathItem> pathItems) {
        LearningPathDTO.PathView view = new LearningPathDTO.PathView();
        view.setId(path.getId());
        view.setUserId(path.getUserId());
        view.setCourseKey(path.getCourseKey());
        view.setGoal(path.getGoal());
        view.setVersion(path.getVersionNo());
        view.setStatus(path.getStatus());
        view.setProfileDigest(path.getProfileDigest());
        view.setMasteryDigest(path.getMasteryDigest());
        view.setSourceMessageId(path.getSourceMessageId());
        view.setGeneratedAt(path.getGeneratedAt());
        view.setNextReplanAt(path.getNextReplanAt());
        view.setItems(pathItems.stream().map(this::toPathItemView).toList());
        return view;
    }

    private LearningPathDTO.PathItemView toPathItemView(LearningPathItem item) {
        LearningPathDTO.PathItemView view = new LearningPathDTO.PathItemView();
        view.setId(item.getId());
        view.setPathId(item.getPathId());
        view.setItemKey(item.getItemKey());
        view.setKnowledgePoint(item.getKnowledgePoint());
        view.setObjective(item.getObjective());
        view.setTargetMastery(item.getTargetMastery());
        view.setPriority(item.getPriority());
        view.setSequenceNo(item.getSequenceNo());
        view.setResourceKinds(readStringList(item.getResourceKindsJson()));
        view.setResourceIds(readStringList(item.getResourceIdsJson()));
        view.setStatus(item.getStatus());
        view.setDeliveryStatus(item.getDeliveryStatus());
        view.setSourceMessageId(item.getSourceMessageId());
        view.setScheduledAt(item.getScheduledAt());
        view.setDeliveredAt(item.getDeliveredAt());
        view.setCompletedAt(item.getCompletedAt());
        view.setRationale(item.getRationale());
        return view;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored learning path resources are malformed", error);
        }
    }

    private TreeSet<Long> readAppliedAttemptIds(LearningKnowledgeMastery mastery) {
        TreeSet<Long> attemptIds = new TreeSet<>();
        String json = mastery.getAppliedAttemptIdsJson();
        if (StringUtils.hasText(json)) {
            try {
                JsonNode root = objectMapper.readTree(json);
                if (root == null || !root.isArray()) {
                    throw new IllegalStateException("Stored assessment receipts are malformed");
                }
                for (JsonNode item : root) {
                    if (!item.isIntegralNumber() || !item.canConvertToLong()) {
                        throw new IllegalStateException("Stored assessment receipts are malformed");
                    }
                    attemptIds.add(item.longValue());
                }
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("Stored assessment receipts are malformed", error);
            }
        }
        if (mastery.getLastAttemptId() != null) {
            attemptIds.add(mastery.getLastAttemptId());
        }
        return attemptIds;
    }

    private String canonicalAttemptIds(Set<Long> attemptIds) {
        try {
            return objectMapper.writeValueAsString(new TreeSet<>(attemptIds));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize assessment receipts", error);
        }
    }

    private LearningPathDTO.MasteryView toMasteryView(LearningKnowledgeMastery mastery) {
        LearningPathDTO.MasteryView view = new LearningPathDTO.MasteryView();
        view.setId(mastery.getId());
        view.setUserId(mastery.getUserId());
        view.setCourseKey(mastery.getCourseKey());
        view.setKnowledgePointKey(mastery.getKnowledgePointKey());
        view.setKnowledgePointName(mastery.getKnowledgePointName());
        view.setLastAttemptId(mastery.getLastAttemptId());
        view.setAttemptCount(mastery.getAttemptCount());
        view.setCorrectCount(mastery.getCorrectCount());
        view.setWrongCount(mastery.getWrongCount());
        view.setScore(mastery.getScore());
        view.setConfidence(mastery.getConfidence());
        view.setStatus(mastery.getStatus());
        view.setNextReviewAt(mastery.getNextReviewAt());
        view.setVersion(mastery.getVersion());
        return view;
    }

    private String normalizeDifficulty(String difficulty) {
        return difficulty == null ? "easy" : difficulty.trim().toLowerCase(Locale.ROOT);
    }

    private int value(Integer number) {
        return number == null ? 0 : number;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(Result.BAD_REQUEST_CODE, message);
    }
}
