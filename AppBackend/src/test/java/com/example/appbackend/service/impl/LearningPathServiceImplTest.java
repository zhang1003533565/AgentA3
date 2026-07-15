package com.example.appbackend.service.impl;

import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.domain.LearningStatuses;
import com.example.appbackend.entity.LearningKnowledgeMastery;
import com.example.appbackend.entity.LearningPath;
import com.example.appbackend.entity.LearningPathItem;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.LearningKnowledgeMasteryRepository;
import com.example.appbackend.repository.LearningPathItemRepository;
import com.example.appbackend.repository.LearningPathRepository;
import com.example.appbackend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningPathServiceImplTest {

    private LearningPathRepository repository;
    private LearningPathItemRepository itemRepository;
    private LearningKnowledgeMasteryRepository masteryRepository;
    private UserRepository userRepository;
    private LearningPathServiceImpl service;
    private List<LearningPath> paths;
    private List<LearningPathItem> items;
    private List<LearningKnowledgeMastery> masteries;
    private PathRepositoryHandler pathStore;
    private ItemRepositoryHandler itemStore;
    private MasteryRepositoryHandler masteryStore;
    private UserRepositoryHandler userStore;

    @BeforeEach
    void setUp() {
        paths = new ArrayList<>();
        items = new ArrayList<>();
        masteries = new ArrayList<>();
        pathStore = new PathRepositoryHandler(paths);
        itemStore = new ItemRepositoryHandler(items, paths);
        masteryStore = new MasteryRepositoryHandler(masteries);
        userStore = new UserRepositoryHandler();
        repository = pathStore.proxy();
        itemRepository = itemStore.proxy();
        masteryRepository = masteryStore.proxy();
        userRepository = userStore.proxy();
        service = new LearningPathServiceImpl(
                repository, itemRepository, masteryRepository, userRepository,
                new ObjectMapper());
    }

    @Test
    void replaceActivePathArchivesPreviousVersion() {
        LearningPathDTO.PathView first = service.replaceActivePath(7L, draft("python", "期末复习"));
        LearningPathDTO.PathView second = service.replaceActivePath(7L, draft("python", "补强列表切片"));

        assertEquals(1, first.getVersion());
        assertEquals(2, second.getVersion());
        assertEquals("active", second.getStatus());
        assertEquals(1, repository.countByUserIdAndCourseKeyAndStatus(
                7L, "python", "active"));
    }

    @Test
    void replaceActivePathArchivesActiveRowEvenWhenLatestVersionIsCompleted() {
        LearningPath active = path(7L, 1, "active");
        paths.add(active);
        paths.add(path(7L, 2, "completed"));

        LearningPathDTO.PathView replacement = service.replaceActivePath(
                7L, draft("python", "重新规划"));

        assertEquals("archived", active.getStatus());
        assertEquals(3, replacement.getVersion());
        assertEquals(1, repository.countByUserIdAndCourseKeyAndStatus(
                7L, "python", "active"));
    }

    @Test
    void repeatedAssessmentIsIdempotentByAttemptId() {
        service.applyAssessment(observation(
                7L, 99L, "python.lists.slicing", false, "medium"));
        service.applyAssessment(observation(
                7L, 99L, "python.lists.slicing", false, "medium"));

        assertEquals(1, masteryRepository
                .findByUserIdAndCourseKeyAndKnowledgePointKey(
                        7L, "python", "python.lists.slicing")
                .orElseThrow()
                .getAttemptCount());
    }

    @Test
    void replayedOlderAssessmentRemainsIdempotentAfterANewerAttempt() {
        service.applyAssessment(observation(
                7L, 99L, "python.lists.slicing", false, "medium"));
        service.applyAssessment(observation(
                7L, 100L, "python.lists.slicing", true, "hard"));

        LearningPathDTO.MasteryView replayed = service.applyAssessment(observation(
                7L, 99L, "python.lists.slicing", false, "medium"));

        assertEquals(2, replayed.getAttemptCount());
        assertEquals(1, replayed.getCorrectCount());
        assertEquals(1, replayed.getWrongCount());
        assertEquals(new BigDecimal("40.00"), replayed.getScore());
    }

    @Test
    void knowledgePointKeyIsTrimmedBeforeLookupAndPersistence() {
        service.applyAssessment(observation(
                7L, 101L, "python.lists.slicing", false, "medium"));

        LearningPathDTO.MasteryView updated = service.applyAssessment(observation(
                7L, 102L, "  python.lists.slicing  ", true, "hard"));

        assertEquals(1, masteries.size());
        assertEquals("python.lists.slicing", updated.getKnowledgePointKey());
        assertEquals(2, updated.getAttemptCount());
    }

    @Test
    void statusCatalogUsesOnlyTheRequiredValues() {
        assertEquals(Set.of("active", "completed", "archived"), LearningStatuses.PATH);
        assertEquals(Set.of("locked", "ready", "in_progress", "completed", "needs_review"),
                LearningStatuses.ITEM);
        assertEquals(Set.of("new", "weak", "learning", "mastered"), LearningStatuses.MASTERY);
    }

    @Test
    void replaceActivePathStoresCanonicalResourceIds() {
        service.replaceActivePath(7L, draft("python", "期末复习"));

        assertEquals("[\"res_a\",\"res_b\"]", items.getFirst().getResourceIdsJson());
    }

    @Test
    void replaceActivePathRejectsDuplicateItemKeysBeforeArchiving() {
        LearningPath existing = path(7L, 1, "active");
        paths.add(existing);
        LearningPathDTO.PathDraft draft = draft("python", "重复节点");
        LearningPathDTO.PathItemDraft duplicate = pathItemDraft("lists-slicing", 2);
        draft.setItems(List.of(draft.getItems().getFirst(), duplicate));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.replaceActivePath(7L, draft));

        assertEquals(400, error.getCode());
        assertEquals("active", existing.getStatus());
        assertEquals(0, pathStore.saveCalls());
    }

    @Test
    void getHomeReturnsActivePathAndMasterySnapshot() {
        LearningPath active = path(7L, 3, "active");
        paths.add(active);
        items.add(pathItem(active.getId(), 10L));
        LearningKnowledgeMastery mastery = mastery(
                new BigDecimal("70.00"), 2, "learning");
        masteries.add(mastery);

        LearningPathDTO.HomeView home = service.getHome(7L, "python");

        assertEquals(7L, home.getUserId());
        assertEquals("python", home.getCourseKey());
        assertEquals(3, home.getActivePath().getVersion());
        assertEquals("python.lists.slicing", home.getMastery().getFirst().getKnowledgePointKey());
    }

    @Test
    void completeInteractionCompletesTheOwnedItem() {
        LearningPathItem item = ownedReadyItem();

        LearningPathDTO.PathItemView result = service.recordResourceInteraction(
                7L, item.getId(), interaction("complete"));

        assertEquals("completed", result.getStatus());
        assertEquals("completed", result.getDeliveryStatus());
        assertNotNull(result.getDeliveredAt());
        assertNotNull(result.getCompletedAt());
        assertEquals(1, itemStore.saveCalls());
    }

    @Test
    void dismissInteractionChangesDeliveryOnly() {
        LearningPathItem item = ownedReadyItem();
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 7, 15, 9, 30);
        item.setDeliveredAt(deliveredAt);

        LearningPathDTO.PathItemView result = service.recordResourceInteraction(
                7L, item.getId(), interaction("dismiss"));

        assertEquals("ready", result.getStatus());
        assertEquals("dismissed", result.getDeliveryStatus());
        assertEquals(deliveredAt, result.getDeliveredAt());
        assertNull(result.getCompletedAt());
    }

    @Test
    void interactionAcceptsViewAndOpenButRejectsUnknownActions() {
        LearningPathItem item = ownedReadyItem();

        assertEquals("viewed", service.recordResourceInteraction(
                7L, item.getId(), interaction("view")).getDeliveryStatus());
        assertEquals("opened", service.recordResourceInteraction(
                7L, item.getId(), interaction("open")).getDeliveryStatus());
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.recordResourceInteraction(
                        7L, item.getId(), interaction("download")));
        assertEquals(400, error.getCode());
    }

    @Test
    void assessmentUsesDifficultyWeightsAndWeakReviewInterval() {
        LocalDateTime before = LocalDateTime.now();
        LearningPathDTO.MasteryView result = service.applyAssessment(observation(
                7L, 11L, "python.lists.slicing", false, "medium"));
        LocalDateTime after = LocalDateTime.now();

        assertEquals(new BigDecimal("0.00"), result.getScore());
        assertEquals("weak", result.getStatus());
        assertReviewAt(result.getNextReviewAt(), before.plusDays(1), after.plusDays(1));
    }

    @Test
    void assessmentMapsSixtyToLearningWithThreeDayReview() {
        masteries.add(mastery(new BigDecimal("50.00"), 1, "weak"));
        LocalDateTime before = LocalDateTime.now();

        LearningPathDTO.MasteryView result = service.applyAssessment(observation(
                7L, 12L, "python.lists.slicing", true, "hard"));
        LocalDateTime after = LocalDateTime.now();

        assertEquals(new BigDecimal("70.00"), result.getScore());
        assertEquals("learning", result.getStatus());
        assertReviewAt(result.getNextReviewAt(), before.plusDays(3), after.plusDays(3));
    }

    @Test
    void assessmentRequiresThreeAttemptsToBecomeMastered() {
        masteries.add(mastery(new BigDecimal("75.00"), 2, "learning"));
        LocalDateTime before = LocalDateTime.now();

        LearningPathDTO.MasteryView result = service.applyAssessment(observation(
                7L, 13L, "python.lists.slicing", true, "medium"));
        LocalDateTime after = LocalDateTime.now();

        assertEquals(new BigDecimal("82.50"), result.getScore());
        assertEquals(3, result.getAttemptCount());
        assertEquals("mastered", result.getStatus());
        assertReviewAt(result.getNextReviewAt(), before.plusDays(7), after.plusDays(7));
    }

    @Test
    void scoreAtLeastEightyWithoutThreeAttemptsRemainsNew() {
        masteries.add(mastery(new BigDecimal("80.00"), 1, "new"));

        LearningPathDTO.MasteryView result = service.applyAssessment(observation(
                7L, 14L, "python.lists.slicing", true, "easy"));

        assertEquals(new BigDecimal("84.00"), result.getScore());
        assertEquals(2, result.getAttemptCount());
        assertEquals("new", result.getStatus());
        assertNull(result.getNextReviewAt());
    }

    private LearningPathDTO.PathDraft draft(String courseKey, String goal) {
        LearningPathDTO.PathItemDraft item = pathItemDraft("lists-slicing", 1);

        LearningPathDTO.PathDraft draft = new LearningPathDTO.PathDraft();
        draft.setCourseKey(courseKey);
        draft.setGoal(goal);
        draft.setProfileDigest("profile-v1");
        draft.setMasteryDigest("mastery-v1");
        draft.setSourceMessageId(501L);
        draft.setGeneratedAt(LocalDateTime.of(2026, 7, 15, 8, 0));
        draft.setNextReplanAt(LocalDateTime.of(2026, 7, 22, 8, 0));
        draft.setItems(List.of(item));
        return draft;
    }

    private LearningPathDTO.PathItemDraft pathItemDraft(String itemKey, int sequenceNo) {
        LearningPathDTO.PathItemDraft item = new LearningPathDTO.PathItemDraft();
        item.setItemKey(itemKey);
        item.setKnowledgePoint("python.lists.slicing");
        item.setObjective("掌握列表切片");
        item.setTargetMastery(new BigDecimal("80.00"));
        item.setPriority(1);
        item.setSequenceNo(sequenceNo);
        item.setResourceKinds(List.of("knowledge_note", "practice_set"));
        item.setResourceIds(List.of("res_b", "res_a"));
        item.setStatus("ready");
        item.setDeliveryStatus("pending");
        item.setSourceMessageId(501L);
        item.setScheduledAt(LocalDateTime.of(2026, 7, 16, 8, 0));
        item.setRationale("优先补强薄弱知识点");
        return item;
    }

    private LearningPathDTO.AssessmentObservation observation(
            Long userId,
            Long attemptId,
            String knowledgePointKey,
            boolean correct,
            String difficulty) {
        LearningPathDTO.AssessmentObservation observation =
                new LearningPathDTO.AssessmentObservation();
        observation.setUserId(userId);
        observation.setAttemptId(attemptId);
        observation.setCourseKey("python");
        observation.setKnowledgePointKey(knowledgePointKey);
        observation.setKnowledgePointName("列表切片");
        observation.setCorrect(correct);
        observation.setDifficulty(difficulty);
        return observation;
    }

    private LearningPath path(Long userId, int version, String status) {
        LearningPath path = new LearningPath();
        path.setId((long) version);
        path.setUserId(userId);
        path.setCourseKey("python");
        path.setGoal("目标");
        path.setVersionNo(version);
        path.setStatus(status);
        path.setProfileDigest("profile-v1");
        path.setMasteryDigest("mastery-v1");
        path.setGeneratedAt(LocalDateTime.of(2026, 7, 15, 8, 0));
        return path;
    }

    private LearningPathItem pathItem(Long pathId, Long itemId) {
        LearningPathItem item = new LearningPathItem();
        item.setId(itemId);
        item.setPathId(pathId);
        item.setItemKey("lists-slicing");
        item.setKnowledgePoint("python.lists.slicing");
        item.setObjective("掌握列表切片");
        item.setTargetMastery(new BigDecimal("80.00"));
        item.setPriority(1);
        item.setSequenceNo(1);
        item.setResourceKindsJson("[\"knowledge_note\"]");
        item.setResourceIdsJson("[\"res_a\"]");
        item.setStatus("ready");
        item.setDeliveryStatus("pending");
        return item;
    }

    private LearningPathItem ownedReadyItem() {
        LearningPath path = path(7L, 1, "active");
        paths.add(path);
        LearningPathItem item = pathItem(path.getId(), 10L);
        items.add(item);
        return item;
    }

    private LearningKnowledgeMastery mastery(BigDecimal score, int attempts, String status) {
        LearningKnowledgeMastery mastery = new LearningKnowledgeMastery();
        mastery.setId(1L);
        mastery.setUserId(7L);
        mastery.setCourseKey("python");
        mastery.setKnowledgePointKey("python.lists.slicing");
        mastery.setKnowledgePointName("列表切片");
        mastery.setAttemptCount(attempts);
        mastery.setCorrectCount(attempts);
        mastery.setWrongCount(0);
        mastery.setScore(score);
        mastery.setConfidence(BigDecimal.ZERO.setScale(4));
        mastery.setStatus(status);
        return mastery;
    }

    private LearningPathDTO.InteractionRequest interaction(String action) {
        LearningPathDTO.InteractionRequest request = new LearningPathDTO.InteractionRequest();
        request.setAction(action);
        return request;
    }

    private void assertReviewAt(
            LocalDateTime actual, LocalDateTime earliest, LocalDateTime latest) {
        assertNotNull(actual);
        assertTrue(!actual.isBefore(earliest));
        assertTrue(!actual.isAfter(latest));
    }

    private abstract static class RepositoryHandler<T> implements InvocationHandler {
        private final Class<T> repositoryType;

        private RepositoryHandler(Class<T> repositoryType) {
            this.repositoryType = repositoryType;
        }

        @SuppressWarnings("unchecked")
        protected final T proxy() {
            return (T) Proxy.newProxyInstance(
                    repositoryType.getClassLoader(), new Class<?>[]{repositoryType}, this);
        }

        protected Object objectMethod(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() != Object.class) {
                throw new UnsupportedOperationException(method.getName());
            }
            return switch (method.getName()) {
                case "toString" -> repositoryType.getSimpleName() + "Proxy";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static final class PathRepositoryHandler
            extends RepositoryHandler<LearningPathRepository> {
        private final List<LearningPath> paths;
        private long nextId = 1L;
        private int saveCalls;

        private PathRepositoryHandler(List<LearningPath> paths) {
            super(LearningPathRepository.class);
            this.paths = paths;
        }

        private int saveCalls() {
            return saveCalls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findTopByUserIdAndCourseKeyOrderByVersionNoDesc" -> paths.stream()
                        .filter(path -> args[0].equals(path.getUserId()))
                        .filter(path -> args[1].equals(path.getCourseKey()))
                        .max(Comparator.comparing(LearningPath::getVersionNo));
                case "findByUserIdAndCourseKeyAndStatus" -> paths.stream()
                        .filter(path -> args[0].equals(path.getUserId()))
                        .filter(path -> args[1].equals(path.getCourseKey()))
                        .filter(path -> args[2].equals(path.getStatus()))
                        .findFirst();
                case "countByUserIdAndCourseKeyAndStatus" -> paths.stream()
                        .filter(path -> args[0].equals(path.getUserId()))
                        .filter(path -> args[1].equals(path.getCourseKey()))
                        .filter(path -> args[2].equals(path.getStatus()))
                        .count();
                case "save" -> save((LearningPath) args[0]);
                default -> objectMethod(proxy, method, args);
            };
        }

        private LearningPath save(LearningPath path) {
            saveCalls++;
            if (path.getId() == null) {
                path.setId(nextId++);
                paths.add(path);
            }
            return path;
        }
    }

    private static final class ItemRepositoryHandler
            extends RepositoryHandler<LearningPathItemRepository> {
        private final List<LearningPathItem> items;
        private final List<LearningPath> paths;
        private long nextId = 1L;
        private int saveCalls;

        private ItemRepositoryHandler(
                List<LearningPathItem> items, List<LearningPath> paths) {
            super(LearningPathItemRepository.class);
            this.items = items;
            this.paths = paths;
        }

        private int saveCalls() {
            return saveCalls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "saveAll" -> saveAll(args[0]);
                case "save" -> save((LearningPathItem) args[0]);
                case "findByPathIdOrderBySequenceNoAscIdAsc" -> items.stream()
                        .filter(item -> args[0].equals(item.getPathId()))
                        .sorted(Comparator.comparing(LearningPathItem::getSequenceNo)
                                .thenComparing(LearningPathItem::getId))
                        .toList();
                case "findOwnedActiveByIdForUpdate" -> items.stream()
                        .filter(item -> args[0].equals(item.getId()))
                        .filter(item -> paths.stream().anyMatch(path -> path.getId().equals(item.getPathId())
                                && args[1].equals(path.getUserId())
                                && args[2].equals(path.getCourseKey())
                                && args[3].equals(path.getStatus())))
                        .findFirst();
                default -> objectMethod(proxy, method, args);
            };
        }

        private List<LearningPathItem> saveAll(Object source) {
            List<LearningPathItem> saved = new ArrayList<>();
            ((Iterable<?>) source).forEach(value -> saved.add(saveNew((LearningPathItem) value)));
            return saved;
        }

        private LearningPathItem saveNew(LearningPathItem item) {
            if (item.getId() == null) {
                item.setId(nextId++);
                items.add(item);
            }
            return item;
        }

        private LearningPathItem save(LearningPathItem item) {
            saveCalls++;
            return saveNew(item);
        }
    }

    private static final class MasteryRepositoryHandler
            extends RepositoryHandler<LearningKnowledgeMasteryRepository> {
        private final List<LearningKnowledgeMastery> masteries;
        private long nextId = 1L;

        private MasteryRepositoryHandler(List<LearningKnowledgeMastery> masteries) {
            super(LearningKnowledgeMasteryRepository.class);
            this.masteries = masteries;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findByUserIdAndCourseKeyAndKnowledgePointKey" -> masteries.stream()
                        .filter(mastery -> args[0].equals(mastery.getUserId()))
                        .filter(mastery -> args[1].equals(mastery.getCourseKey()))
                        .filter(mastery -> args[2].equals(mastery.getKnowledgePointKey()))
                        .findFirst();
                case "findByUserIdAndCourseKeyOrderByKnowledgePointKeyAsc" -> masteries.stream()
                        .filter(mastery -> args[0].equals(mastery.getUserId()))
                        .filter(mastery -> args[1].equals(mastery.getCourseKey()))
                        .sorted(Comparator.comparing(LearningKnowledgeMastery::getKnowledgePointKey))
                        .toList();
                case "save" -> save((LearningKnowledgeMastery) args[0]);
                default -> objectMethod(proxy, method, args);
            };
        }

        private LearningKnowledgeMastery save(LearningKnowledgeMastery mastery) {
            if (mastery.getId() == null) {
                mastery.setId(nextId++);
                masteries.add(mastery);
            }
            return mastery;
        }
    }

    private static final class UserRepositoryHandler
            extends RepositoryHandler<UserRepository> {

        private UserRepositoryHandler() {
            super(UserRepository.class);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("findByIdForUpdate".equals(method.getName())) {
                User user = new User();
                user.setId((Long) args[0]);
                user.setUsername("learning-test-user");
                user.setPassword("test-only-password");
                return Optional.of(user);
            }
            return objectMethod(proxy, method, args);
        }
    }
}
