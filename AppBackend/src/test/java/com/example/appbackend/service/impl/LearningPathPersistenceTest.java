package com.example.appbackend.service.impl;

import com.example.appbackend.config.ObjectMapperConfig;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.entity.LearningKnowledgeMastery;
import com.example.appbackend.entity.LearningPath;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.repository.LearningKnowledgeMasteryRepository;
import com.example.appbackend.repository.LearningPathItemRepository;
import com.example.appbackend.repository.LearningPathRepository;
import com.example.appbackend.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("learning-jpa-test")
@Import({LearningPathServiceImpl.class, ObjectMapperConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LearningPathPersistenceTest {

    private final LearningPathServiceImpl service;
    private final UserRepository userRepository;
    private final LearningPathRepository pathRepository;
    private final LearningPathItemRepository itemRepository;
    private final LearningKnowledgeMasteryRepository masteryRepository;
    private final ExamQuestionRepository questionRepository;
    private final PlatformTransactionManager transactionManager;
    private Long userId;

    @Autowired
    LearningPathPersistenceTest(LearningPathServiceImpl service,
                                UserRepository userRepository,
                                LearningPathRepository pathRepository,
                                LearningPathItemRepository itemRepository,
                                LearningKnowledgeMasteryRepository masteryRepository,
                                ExamQuestionRepository questionRepository,
                                PlatformTransactionManager transactionManager) {
        this.service = service;
        this.userRepository = userRepository;
        this.pathRepository = pathRepository;
        this.itemRepository = itemRepository;
        this.masteryRepository = masteryRepository;
        this.questionRepository = questionRepository;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void setUp() {
        itemRepository.deleteAll();
        pathRepository.deleteAll();
        masteryRepository.deleteAll();
        questionRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("learning-path-user");
        user.setPassword("test-only-password");
        user.setStatus(1);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @Test
    void persistsCanonicalAttemptReceiptsAndTrimmedKnowledgePointKey() {
        service.applyAssessment(observation(200L, "  python.lists.slicing  ", false));
        service.applyAssessment(observation(199L, "python.lists.slicing", true));

        LearningKnowledgeMastery stored = masteryRepository
                .findByUserIdAndCourseKeyAndKnowledgePointKey(
                        userId, "python", "python.lists.slicing")
                .orElseThrow();
        assertEquals(1, masteryRepository
                .findByUserIdAndCourseKeyOrderByKnowledgePointKeyAsc(userId, "python")
                .size());
        assertEquals("python.lists.slicing", stored.getKnowledgePointKey());
        assertEquals("[199,200]", stored.getAppliedAttemptIdsJson());
        assertEquals(2, stored.getAttemptCount());
    }

    @Test
    void concurrentSameAssessmentIsAppliedExactlyOnce() throws Exception {
        List<LearningPathDTO.MasteryView> results = runConcurrently(List.of(
                () -> service.applyAssessment(observation(
                        301L, "python.lists.slicing", false)),
                () -> service.applyAssessment(observation(
                        301L, "python.lists.slicing", false))));

        LearningKnowledgeMastery stored = masteryRepository
                .findByUserIdAndCourseKeyAndKnowledgePointKey(
                        userId, "python", "python.lists.slicing")
                .orElseThrow();
        assertEquals(List.of(1, 1), results.stream()
                .map(LearningPathDTO.MasteryView::getAttemptCount)
                .sorted()
                .toList());
        assertEquals(1, stored.getAttemptCount());
        assertEquals("[301]", stored.getAppliedAttemptIdsJson());
    }

    @Test
    void concurrentPathReplacementProducesConsecutiveVersionsAndOneActivePath()
            throws Exception {
        List<LearningPathDTO.PathView> results = runConcurrently(List.of(
                () -> service.replaceActivePath(userId, draft("并发目标 A")),
                () -> service.replaceActivePath(userId, draft("并发目标 B")),
                () -> service.replaceActivePath(userId, draft("并发目标 C"))));

        assertEquals(List.of(1, 2, 3), results.stream()
                .map(LearningPathDTO.PathView::getVersion)
                .sorted()
                .toList());
        assertEquals(1, pathRepository.countByUserIdAndCourseKeyAndStatus(
                userId, "python", "active"));
        assertEquals(List.of(1, 2, 3), pathRepository.findAll().stream()
                .filter(path -> userId.equals(path.getUserId()))
                .sorted(Comparator.comparing(LearningPath::getVersionNo))
                .map(LearningPath::getVersionNo)
                .toList());
    }

    @Test
    void feedbackContextHoldsTheUserLockUntilTheOuterExamTransactionCompletes()
            throws Exception {
        service.replaceActivePath(userId, draft("初始学习路径"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch feedbackLocked = new CountDownLatch(1);
        CountDownLatch releaseFeedback = new CountDownLatch(1);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            Future<LearningPathDTO.HomeView> feedback = executor.submit(() -> transaction.execute(status -> {
                LearningPathDTO.HomeView home = service.getHomeForFeedback(userId, "python");
                feedbackLocked.countDown();
                try {
                    if (!releaseFeedback.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Feedback lock was not released");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                return home;
            }));
            if (!feedbackLocked.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Feedback transaction did not acquire the user lock");
            }
            Future<LearningPathDTO.PathView> replacement = executor.submit(() -> transaction.execute(status -> {
                replacementStarted.countDown();
                return service.replaceActivePath(userId, draft("并发路径生成"));
            }));
            if (!replacementStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Replacement transaction did not start");
            }

            assertThrows(TimeoutException.class,
                    () -> replacement.get(250, TimeUnit.MILLISECONDS));
            releaseFeedback.countDown();

            assertEquals(1, feedback.get(5, TimeUnit.SECONDS).getActivePath().getVersion());
            assertEquals(2, replacement.get(5, TimeUnit.SECONDS).getVersion());
            assertEquals(1, pathRepository.countByUserIdAndCourseKeyAndStatus(
                    userId, "python", "active"));
        } finally {
            releaseFeedback.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void feedbackAfterAnOlderRepeatableReadSnapshotSeesTheCommittedReplacement()
            throws Exception {
        assertEquals(1, service.replaceActivePath(
                userId, draft("初始学习路径")).getVersion());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch snapshotEstablished = new CountDownLatch(1);
        CountDownLatch replacementPrepared = new CountDownLatch(1);
        CountDownLatch feedbackAttempted = new CountDownLatch(1);
        CountDownLatch releaseReplacement = new CountDownLatch(1);
        TransactionTemplate feedbackTransaction = new TransactionTemplate(transactionManager);
        feedbackTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        TransactionTemplate replacementTransaction = new TransactionTemplate(transactionManager);
        try {
            Future<FeedbackVersions> feedback = executor.submit(() ->
                    feedbackTransaction.execute(status -> {
                        questionRepository.count();
                        snapshotEstablished.countDown();
                        await(replacementPrepared, "Replacement transaction did not prepare v2");
                        feedbackAttempted.countDown();

                        LearningPathDTO.HomeView current = service.getHomeForFeedback(userId, "python");
                        LearningPathDTO.PathView generated = service.replaceActivePath(
                                userId, draft("考试反馈路径"));
                        return new FeedbackVersions(
                                current.getActivePath().getVersion(), generated.getVersion());
                    }));
            await(snapshotEstablished, "Exam transaction did not establish its old snapshot");

            Future<LearningPathDTO.PathView> replacement = executor.submit(() ->
                    replacementTransaction.execute(status -> {
                        LearningPathDTO.PathView prepared = service.replaceActivePath(
                                userId, draft("并发生成路径"));
                        replacementPrepared.countDown();
                        await(releaseReplacement, "Replacement transaction was not released");
                        return prepared;
                    }));
            await(replacementPrepared, "Replacement transaction did not acquire the user lock");
            await(feedbackAttempted, "Feedback transaction did not start waiting for the user lock");

            assertThrows(TimeoutException.class,
                    () -> feedback.get(250, TimeUnit.MILLISECONDS));
            releaseReplacement.countDown();

            assertEquals(2, replacement.get(5, TimeUnit.SECONDS).getVersion());
            FeedbackVersions versions = feedback.get(5, TimeUnit.SECONDS);
            assertEquals(2, versions.baselineVersion());
            assertEquals(3, versions.generatedVersion());
            assertEquals(List.of(1, 2, 3), pathRepository.findAll().stream()
                    .filter(path -> userId.equals(path.getUserId()))
                    .sorted(Comparator.comparing(LearningPath::getVersionNo))
                    .map(LearningPath::getVersionNo)
                    .toList());
            assertEquals(1, pathRepository.countByUserIdAndCourseKeyAndStatus(
                    userId, "python", "active"));
        } finally {
            releaseReplacement.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void feedbackAndMutationRepositoriesDeclarePessimisticCurrentReads()
            throws Exception {
        assertPessimisticCurrentRead(LearningPathRepository.class.getMethod(
                "findActiveForUpdate", Long.class, String.class, String.class));
        assertPessimisticCurrentRead(LearningPathRepository.class.getMethod(
                "findLatestForUpdate", Long.class, String.class, Pageable.class));
        assertPessimisticCurrentRead(LearningPathItemRepository.class.getMethod(
                "findByPathIdForUpdate", Long.class));
        assertPessimisticCurrentRead(LearningKnowledgeMasteryRepository.class.getMethod(
                "findByUserIdAndCourseKeyForUpdate", Long.class, String.class));
        assertPessimisticCurrentRead(LearningKnowledgeMasteryRepository.class.getMethod(
                "findOneForUpdate", Long.class, String.class, String.class));
    }

    private LearningPathDTO.AssessmentObservation observation(
            Long attemptId, String knowledgePointKey, boolean correct) {
        LearningPathDTO.AssessmentObservation observation =
                new LearningPathDTO.AssessmentObservation();
        observation.setUserId(userId);
        observation.setAttemptId(attemptId);
        observation.setCourseKey("python");
        observation.setKnowledgePointKey(knowledgePointKey);
        observation.setKnowledgePointName("列表切片");
        observation.setCorrect(correct);
        observation.setDifficulty("hard");
        return observation;
    }

    private LearningPathDTO.PathDraft draft(String goal) {
        LearningPathDTO.PathItemDraft item = new LearningPathDTO.PathItemDraft();
        item.setItemKey("lists-slicing");
        item.setKnowledgePoint("python.lists.slicing");
        item.setObjective("掌握列表切片");
        item.setTargetMastery(new BigDecimal("80.00"));
        item.setPriority(1);
        item.setSequenceNo(1);
        item.setResourceKinds(List.of("knowledge_note"));
        item.setResourceIds(List.of("res_a"));
        item.setStatus("ready");
        item.setDeliveryStatus("pending");
        item.setScheduledAt(LocalDateTime.of(2026, 7, 16, 8, 0));

        LearningPathDTO.PathDraft draft = new LearningPathDTO.PathDraft();
        draft.setCourseKey("python");
        draft.setGoal(goal);
        draft.setProfileDigest("profile-v1");
        draft.setMasteryDigest("mastery-v1");
        draft.setGeneratedAt(LocalDateTime.of(2026, 7, 15, 8, 0));
        draft.setItems(List.of(item));
        return draft;
    }

    private void assertPessimisticCurrentRead(Method method) {
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock, method.getName());
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value(), method.getName());
        assertNotNull(method.getAnnotation(Query.class), method.getName());
    }

    private void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private record FeedbackVersions(int baselineVersion, int generatedVersion) {
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent workers did not become ready");
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
