package com.example.appbackend.service.impl;

import com.example.appbackend.config.ObjectMapperConfig;
import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.entity.ExamPaperAttempt;
import com.example.appbackend.entity.ExamPaperAttemptAnswer;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.LearningKnowledgeMastery;
import com.example.appbackend.entity.LearningPath;
import com.example.appbackend.entity.LearningPathItem;
import com.example.appbackend.entity.User;
import com.example.appbackend.entity.UserProfileEvidence;
import com.example.appbackend.repository.ExamPaperAttemptAnswerRepository;
import com.example.appbackend.repository.ExamPaperAttemptRepository;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.repository.LearningKnowledgeMasteryRepository;
import com.example.appbackend.repository.LearningPathItemRepository;
import com.example.appbackend.repository.LearningPathRepository;
import com.example.appbackend.repository.UserProfileDimensionRepository;
import com.example.appbackend.repository.UserProfileEvidenceRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("learning-jpa-test")
@Import({
        AppExamServiceImpl.class,
        LearningPathServiceImpl.class,
        UserProfileServiceImpl.class,
        ObjectMapperConfig.class,
        AppExamLearningFeedbackPersistenceTest.TestDependencies.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppExamLearningFeedbackPersistenceTest {

    private static final long PAPER_ID = 7001L;
    private static final String KNOWLEDGE_POINT = "python.lists.slicing";

    private final AppExamServiceImpl examService;
    private final LearningPathServiceImpl learningPathService;
    private final UserRepository userRepository;
    private final ExamQuestionRepository questionRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final ExamPaperAttemptRepository attemptRepository;
    private final ExamPaperAttemptAnswerRepository answerRepository;
    private final LearningKnowledgeMasteryRepository masteryRepository;
    private final LearningPathRepository pathRepository;
    private final LearningPathItemRepository pathItemRepository;
    private final UserProfileDimensionRepository profileDimensionRepository;
    private final UserProfileEvidenceRepository profileEvidenceRepository;

    private Long userId;
    private Long attemptId;

    @Autowired
    AppExamLearningFeedbackPersistenceTest(
            AppExamServiceImpl examService,
            LearningPathServiceImpl learningPathService,
            UserRepository userRepository,
            ExamQuestionRepository questionRepository,
            ExamPaperQuestionRepository paperQuestionRepository,
            ExamPaperAttemptRepository attemptRepository,
            ExamPaperAttemptAnswerRepository answerRepository,
            LearningKnowledgeMasteryRepository masteryRepository,
            LearningPathRepository pathRepository,
            LearningPathItemRepository pathItemRepository,
            UserProfileDimensionRepository profileDimensionRepository,
            UserProfileEvidenceRepository profileEvidenceRepository) {
        this.examService = examService;
        this.learningPathService = learningPathService;
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.masteryRepository = masteryRepository;
        this.pathRepository = pathRepository;
        this.pathItemRepository = pathItemRepository;
        this.profileDimensionRepository = profileDimensionRepository;
        this.profileEvidenceRepository = profileEvidenceRepository;
    }

    @BeforeEach
    void setUp() {
        answerRepository.deleteAll();
        attemptRepository.deleteAll();
        paperQuestionRepository.deleteAll();
        questionRepository.deleteAll();
        pathItemRepository.deleteAll();
        pathRepository.deleteAll();
        masteryRepository.deleteAll();
        profileEvidenceRepository.deleteAll();
        profileDimensionRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setUsername("exam-feedback-integration-user");
        user.setPassword("test-only-password");
        user.setStatus(1);
        userId = userRepository.saveAndFlush(user).getId();

        learningPathService.replaceActivePath(userId, initialPath());

        ExamQuestion sourceQuestion = new ExamQuestion();
        sourceQuestion.setType("single_choice");
        sourceQuestion.setStem("列表切片结果是什么？");
        sourceQuestion.setScore(BigDecimal.TEN);
        sourceQuestion.setDifficulty("hard");
        sourceQuestion.setKnowledgePointsJson("[\"" + KNOWLEDGE_POINT + "\"]");
        sourceQuestion.setBodyJson("{\"options\":[{\"key\":\"A\",\"text\":\"错误\"},{\"key\":\"B\",\"text\":\"正确\"}]}");
        sourceQuestion.setAnswerJson("{\"correctOption\":\"B\"}");
        sourceQuestion.setScoringJson("{}");
        sourceQuestion.setRawQuestionJson("{}");
        sourceQuestion.setStatus(1);
        sourceQuestion = questionRepository.saveAndFlush(sourceQuestion);

        ExamPaperQuestion paperQuestion = new ExamPaperQuestion();
        paperQuestion.setPaperId(PAPER_ID);
        paperQuestion.setQuestionId(sourceQuestion.getId());
        paperQuestion.setSortOrder(1);
        paperQuestion.setSectionOrder(1);
        paperQuestion.setScore(BigDecimal.TEN);
        paperQuestion.setType("single_choice");
        paperQuestion.setStem(sourceQuestion.getStem());
        paperQuestion.setBodyJson(sourceQuestion.getBodyJson());
        paperQuestion.setAnswerJson(sourceQuestion.getAnswerJson());
        paperQuestion.setScoringJson("{}");
        paperQuestion = paperQuestionRepository.saveAndFlush(paperQuestion);

        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 14, 0);
        ExamPaperAttempt attempt = new ExamPaperAttempt();
        attempt.setPaperId(PAPER_ID);
        attempt.setUserId(userId);
        attempt.setAttemptNo(1);
        attempt.setStatus(ExamPaperAttempt.Status.IN_PROGRESS);
        attempt.setStartedAt(now.minusMinutes(10));
        attempt.setDeadlineAt(now.plusMinutes(10));
        attempt.setQuestionCount(1);
        attempt.setAnsweredCount(1);
        attempt = attemptRepository.saveAndFlush(attempt);
        attemptId = attempt.getId();

        ExamPaperAttemptAnswer answer = new ExamPaperAttemptAnswer();
        answer.setAttemptId(attemptId);
        answer.setPaperQuestionId(paperQuestion.getId());
        answer.setAnswerJson("{\"selectedOption\":\"A\"}");
        answer.setAnswered(true);
        answerRepository.saveAndFlush(answer);
    }

    @Test
    void submissionCommitsAttemptMasteryProfileEvidenceAndPathSnapshotTogether() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 7, 15, 14, 0);

        AppExamDTO.AttemptResult submitted = examService.submit(
                attemptId, userId, submittedAt);

        assertThat(submitted.getStatus()).isEqualTo(ExamPaperAttempt.Status.SUBMITTED);
        assertThat(submitted.getObjectiveScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(submitted.getLearningUpdate()).satisfies(update -> {
            assertThat(update.getAssessedKnowledgePoints()).containsExactly(KNOWLEDGE_POINT);
            assertThat(update.getWeakKnowledgePoints()).containsExactly(KNOWLEDGE_POINT);
            assertThat(update.getPathVersionBefore()).isEqualTo(1);
            assertThat(update.getPathVersionAfter()).isEqualTo(2);
            assertThat(update.getReplanned()).isTrue();
            assertThat(update.getProfileEvidence()).hasSize(3);
        });

        ExamPaperAttempt storedAttempt = attemptRepository.findById(attemptId).orElseThrow();
        assertThat(storedAttempt.getStatus()).isEqualTo(ExamPaperAttempt.Status.SUBMITTED);
        assertThat(storedAttempt.getSubmittedAt()).isEqualTo(submittedAt);
        assertThat(storedAttempt.getActiveMarker()).isNull();
        assertThat(storedAttempt.getLearningUpdateJson()).isNotBlank();

        ExamPaperAttemptAnswer storedAnswer = answerRepository.findByAttemptId(attemptId)
                .getFirst();
        assertThat(storedAnswer.getCorrect()).isFalse();
        assertThat(storedAnswer.getScore()).isEqualByComparingTo(BigDecimal.ZERO);

        LearningKnowledgeMastery mastery = masteryRepository
                .findByUserIdAndCourseKeyAndKnowledgePointKey(
                        userId, "python", KNOWLEDGE_POINT)
                .orElseThrow();
        assertThat(mastery.getAttemptCount()).isEqualTo(1);
        assertThat(mastery.getWrongCount()).isEqualTo(1);
        assertThat(mastery.getLastAttemptId()).isEqualTo(attemptId);
        assertThat(mastery.getAppliedAttemptIdsJson()).isEqualTo("[" + attemptId + "]");

        List<UserProfileEvidence> profileEvidence = profileEvidenceRepository.findAll()
                .stream()
                .filter(item -> userId.equals(item.getUserId()))
                .toList();
        assertThat(profileEvidence).hasSize(3);
        assertThat(profileEvidence)
                .extracting(UserProfileEvidence::getDimensionKey)
                .containsExactlyInAnyOrder(
                        "weak_points", "learning_progress", "ability_performance");
        assertThat(profileEvidence)
                .extracting(UserProfileEvidence::getStatus)
                .containsOnly("candidate");

        List<LearningPath> pathVersions = pathRepository.findAll().stream()
                .filter(path -> userId.equals(path.getUserId()))
                .sorted(Comparator.comparing(LearningPath::getVersionNo))
                .toList();
        assertThat(pathVersions)
                .extracting(LearningPath::getVersionNo)
                .containsExactly(1, 2);
        assertThat(pathVersions)
                .extracting(LearningPath::getStatus)
                .containsExactly("archived", "active");
        assertThat(pathRepository.countByUserIdAndCourseKeyAndStatus(
                userId, "python", "active")).isEqualTo(1);
        LearningPath activePath = pathVersions.getLast();
        assertThat(activePath.getMasteryDigest()).hasSizeLessThanOrEqualTo(128);
        List<LearningPathItem> activeItems = pathItemRepository
                .findByPathIdOrderBySequenceNoAscIdAsc(activePath.getId());
        assertThat(activeItems).singleElement().satisfies(item -> {
            assertThat(item.getKnowledgePoint()).isEqualTo(KNOWLEDGE_POINT);
            assertThat(item.getStatus()).isEqualTo("needs_review");
            assertThat(item.getSequenceNo()).isEqualTo(1);
        });

        AppExamDTO.AttemptResult reread = examService.result(attemptId, userId);
        assertThat(reread.getLearningUpdate()).isEqualTo(submitted.getLearningUpdate());
        assertThat(reread.getId()).isEqualTo(submitted.getId());
        assertThat(reread.getStatus()).isEqualTo(submitted.getStatus());
        assertThat(reread.getObjectiveScore())
                .isEqualByComparingTo(submitted.getObjectiveScore());
        assertThat(reread.getObjectiveTotalScore())
                .isEqualByComparingTo(submitted.getObjectiveTotalScore());
        assertThat(reread.getQuestions()).singleElement().satisfies(question -> {
            assertThat(question.getCorrect()).isFalse();
            assertThat(question.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    private LearningPathDTO.PathDraft initialPath() {
        LearningPathDTO.PathItemDraft item = new LearningPathDTO.PathItemDraft();
        item.setItemKey("review-lists");
        item.setKnowledgePoint(KNOWLEDGE_POINT);
        item.setObjective("掌握列表切片");
        item.setTargetMastery(new BigDecimal("80.00"));
        item.setPriority(2);
        item.setSequenceNo(1);
        item.setResourceKinds(List.of("knowledge_note", "practice_set"));
        item.setResourceIds(List.of("python-lists-note"));
        item.setStatus("ready");
        item.setDeliveryStatus("pending");
        item.setScheduledAt(LocalDateTime.of(2026, 7, 15, 8, 0));
        item.setRationale("按基础路径学习");

        LearningPathDTO.PathDraft draft = new LearningPathDTO.PathDraft();
        draft.setCourseKey("python");
        draft.setGoal("补齐 Python 基础");
        draft.setProfileDigest("画像摘要");
        draft.setMasteryDigest("掌握度摘要");
        draft.setGeneratedAt(LocalDateTime.of(2026, 7, 15, 8, 0));
        draft.setItems(List.of(item));
        return draft;
    }

    @TestConfiguration
    static class TestDependencies {

        @Bean
        PythonAiProxyService pythonAiProxyService() {
            return mock(PythonAiProxyService.class);
        }

        @Bean
        SystemConfigService systemConfigService() {
            return mock(SystemConfigService.class);
        }

        @Bean("profileSummaryExecutor")
        Executor profileSummaryExecutor() {
            return Runnable::run;
        }
    }
}
