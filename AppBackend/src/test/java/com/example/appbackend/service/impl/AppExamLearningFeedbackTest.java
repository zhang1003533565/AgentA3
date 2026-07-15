package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.ExamPaperAttempt;
import com.example.appbackend.entity.ExamPaperAttemptAnswer;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.repository.ExamPaperAttemptAnswerRepository;
import com.example.appbackend.repository.ExamPaperAttemptRepository;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.UserProfileService;
import jakarta.persistence.Column;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppExamLearningFeedbackTest {

    @Mock ExamPaperRepository paperRepository;
    @Mock ExamPaperQuestionRepository paperQuestionRepository;
    @Mock ExamPaperAttemptRepository attemptRepository;
    @Mock ExamPaperAttemptAnswerRepository answerRepository;
    @Mock ExamQuestionRepository examQuestionRepository;
    @Mock LearningPathService learningPathService;
    @Mock UserProfileService userProfileService;

    private AppExamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppExamServiceImpl(
                paperRepository,
                paperQuestionRepository,
                attemptRepository,
                answerRepository,
                examQuestionRepository,
                learningPathService,
                userProfileService
        );
    }

    @Test
    void wrongPythonAnswerUpdatesMasteryProfileAndPathOnlyOnFirstSubmission() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
        ExamPaperAttempt attempt = attempt();
        List<ExamPaperQuestion> paperQuestions = List.of(
                paperQuestion(101L, 501L, "single_choice", "{\"correctOption\":\"B\"}"),
                paperQuestion(102L, 502L, "short_answer", "{\"referenceAnswer\":\"解释\"}"),
                paperQuestion(103L, 503L, "true_false", "{\"correct\":true}"));
        List<ExamPaperAttemptAnswer> answers = List.of(
                answer(101L, "{\"selectedOption\":\"A\"}"),
                answer(102L, "{\"text\":\"学生解释\"}"));
        List<ExamQuestion> sourceQuestions = List.of(
                sourceQuestion(501L, "hard", "[\"python.lists.slicing\",\"java.lists\"]"),
                sourceQuestion(502L, "medium", "[\"python.subjective.reasoning\"]"),
                sourceQuestion(503L, "easy", "[\"python.booleans.truth\"]"));

        LearningPathDTO.HomeView home = new LearningPathDTO.HomeView();
        home.setUserId(9L);
        home.setCourseKey("python");
        home.setActivePath(path(71L, 1));
        LearningPathDTO.MasteryView previous = mastery("python.lists.slicing", "40.00", "weak");
        home.setMastery(List.of(previous));

        LearningPathDTO.MasteryView current = mastery("python.lists.slicing", "24.00", "weak");
        current.setAttemptCount(2);

        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(attempt));
        when(attemptRepository.findByIdAndUserId(41L, 9L)).thenReturn(Optional.of(attempt));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L))
                .thenReturn(paperQuestions);
        when(answerRepository.findByAttemptId(41L)).thenReturn(answers);
        when(examQuestionRepository.findAllById(any())).thenReturn(sourceQuestions);
        when(learningPathService.getHome(9L, "python")).thenReturn(home);
        when(learningPathService.applyAssessment(any())).thenReturn(current);
        when(learningPathService.replaceActivePath(anyLong(), any())).thenReturn(path(72L, 2));
        when(userProfileService.addEvidence(anyLong(), any())).thenAnswer(invocation -> {
            UserProfileDTO.EvidenceRequest request = invocation.getArgument(1);
            UserProfileDTO.EvidenceResponse response = new UserProfileDTO.EvidenceResponse();
            response.setDimensionKey(request.getDimensionKey());
            response.setStatus("candidate");
            response.setAccepted(false);
            return response;
        });
        when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AppExamDTO.AttemptResult first = service.submit(41L, 9L, now);
        AppExamDTO.AttemptResult repeatedSubmit = service.submit(41L, 9L, now.plusMinutes(1));
        AppExamDTO.AttemptResult repeatedResult = service.result(41L, 9L);

        assertThat(first.getLearningUpdate().getAssessedKnowledgePoints())
                .containsExactly("python.lists.slicing");
        assertThat(first.getLearningUpdate().getSchemaVersion())
                .isEqualTo("exam-learning-update-v1");
        assertThat(first.getLearningUpdate().getWeakKnowledgePoints())
                .containsExactly("python.lists.slicing");
        assertThat(first.getLearningUpdate().getPathVersionBefore()).isEqualTo(1);
        assertThat(first.getLearningUpdate().getPathVersionAfter()).isEqualTo(2);
        assertThat(first.getLearningUpdate().getReplanned()).isTrue();
        assertThat(first.getLearningUpdate().getReplanReason()).contains("python.lists.slicing");
        assertThat(first.getLearningUpdate().getMasteryChanges()).singleElement()
                .satisfies(change -> {
                    assertThat(change.getKnowledgePointKey()).isEqualTo("python.lists.slicing");
                    assertThat(change.getScoreBefore()).isEqualByComparingTo("40.00");
                    assertThat(change.getScoreAfter()).isEqualByComparingTo("24.00");
                    assertThat(change.getCorrect()).isFalse();
                });
        assertThat(first.getLearningUpdate().getProfileEvidence())
                .extracting(AppExamDTO.ProfileEvidenceUpdate::getDimensionKey)
                .containsExactly("weak_points", "learning_progress", "ability_performance");
        assertThat(first.getLearningUpdate().getChangedNodes()).isNotEmpty();
        assertThat(first.getLearningUpdate().getNextRecommendation().getItemKey())
                .isEqualTo("review-lists");
        assertThat(attempt.getLearningUpdateJson()).isNotBlank();
        assertThat(repeatedSubmit).isEqualTo(first);
        assertThat(repeatedResult).isEqualTo(first);
        assertThat(repeatedSubmit.getLearningUpdate()).isEqualTo(first.getLearningUpdate());
        assertThat(repeatedResult.getLearningUpdate()).isEqualTo(first.getLearningUpdate());

        ArgumentCaptor<LearningPathDTO.AssessmentObservation> observationCaptor =
                ArgumentCaptor.forClass(LearningPathDTO.AssessmentObservation.class);
        verify(learningPathService, times(1)).applyAssessment(observationCaptor.capture());
        assertThat(observationCaptor.getValue().getAttemptId()).isEqualTo(41L);
        assertThat(observationCaptor.getValue().getKnowledgePointKey())
                .isEqualTo("python.lists.slicing");
        assertThat(observationCaptor.getValue().getDifficulty()).isEqualTo("hard");
        assertThat(observationCaptor.getValue().getCorrect()).isFalse();
        verify(learningPathService, times(1)).replaceActivePath(anyLong(), any());
        verify(userProfileService, times(3)).addEvidence(anyLong(), any());
        verify(attemptRepository, times(1)).save(attempt);
    }

    @Test
    void malformedNonCanonicalAndNonPythonKnowledgePointsNeverCreateLearningSideEffects() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 11, 0);
        ExamPaperAttempt attempt = attempt();
        ExamPaperQuestion question = paperQuestion(
                101L, 501L, "single_choice", "{\"correctOption\":\"B\"}");
        ExamPaperAttemptAnswer answer = answer(101L, "{\"selectedOption\":\"B\"}");
        ExamQuestion source = sourceQuestion(
                501L,
                "easy",
                "[\"Python.lists\",\"python\",\"python..lists\",\"python.lists space\",\"java.lists\"]");

        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(attempt));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L))
                .thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(41L)).thenReturn(List.of(answer));
        when(examQuestionRepository.findAllById(any())).thenReturn(List.of(source));
        when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AppExamDTO.AttemptResult result = service.submit(41L, 9L, now);

        assertThat(result.getLearningUpdate()).isNull();
        assertThat(attempt.getLearningUpdateJson()).isNull();
        verify(learningPathService, never()).getHome(anyLong(), any());
        verify(learningPathService, never()).applyAssessment(any());
        verify(learningPathService, never()).replaceActivePath(anyLong(), any());
        verify(userProfileService, never()).addEvidence(anyLong(), any());
    }

    @Test
    void repeatedKnowledgePointInsideOneAttemptIsAggregatedToOneConservativeObservation() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 11, 30);
        ExamPaperAttempt attempt = attempt();
        List<ExamPaperQuestion> paperQuestions = List.of(
                paperQuestion(101L, 501L, "single_choice", "{\"correctOption\":\"B\"}"),
                paperQuestion(102L, 502L, "true_false", "{\"correct\":true}"));
        List<ExamPaperAttemptAnswer> answers = List.of(
                answer(101L, "{\"selectedOption\":\"B\"}"),
                answer(102L, "{\"value\":false}"));
        List<ExamQuestion> sourceQuestions = List.of(
                sourceQuestion(501L, "easy", "[\"python.control.conditions\"]"),
                sourceQuestion(502L, "hard", "[\"python.control.conditions\"]"));
        LearningPathDTO.HomeView emptyHome = new LearningPathDTO.HomeView();
        emptyHome.setCourseKey("python");
        emptyHome.setMastery(List.of());
        LearningPathDTO.MasteryView current = mastery(
                "python.control.conditions", "0.00", "weak");

        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(attempt));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L))
                .thenReturn(paperQuestions);
        when(answerRepository.findByAttemptId(41L)).thenReturn(answers);
        when(examQuestionRepository.findAllById(any())).thenReturn(sourceQuestions);
        when(learningPathService.getHome(9L, "python")).thenReturn(emptyHome);
        when(learningPathService.applyAssessment(any())).thenReturn(current);
        when(userProfileService.addEvidence(anyLong(), any())).thenAnswer(invocation -> {
            UserProfileDTO.EvidenceRequest request = invocation.getArgument(1);
            UserProfileDTO.EvidenceResponse response = new UserProfileDTO.EvidenceResponse();
            response.setDimensionKey(request.getDimensionKey());
            response.setStatus("candidate");
            return response;
        });
        when(attemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AppExamDTO.AttemptResult result = service.submit(41L, 9L, now);

        ArgumentCaptor<LearningPathDTO.AssessmentObservation> captor =
                ArgumentCaptor.forClass(LearningPathDTO.AssessmentObservation.class);
        verify(learningPathService, times(1)).applyAssessment(captor.capture());
        assertThat(captor.getValue().getKnowledgePointKey())
                .isEqualTo("python.control.conditions");
        assertThat(captor.getValue().getCorrect()).isFalse();
        assertThat(captor.getValue().getDifficulty()).isEqualTo("hard");
        assertThat(result.getLearningUpdate().getWeakKnowledgePoints())
                .containsExactly("python.control.conditions");
        assertThat(result.getLearningUpdate().getReplanned()).isFalse();
        verify(learningPathService, never()).replaceActivePath(anyLong(), any());
    }

    @Test
    void learningUpdateIsStoredAsACompatibleLongTextSnapshot() throws Exception {
        Column column = ExamPaperAttempt.class.getDeclaredField("learningUpdateJson")
                .getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.name()).isEqualTo("learning_update_json");
        assertThat(column.nullable()).isTrue();
        assertThat(column.columnDefinition()).startsWith("LONGTEXT");
        assertThat(AppExamDTO.AttemptResult.class.getDeclaredField("learningUpdate")).isNotNull();
    }

    private ExamPaperAttempt attempt() {
        ExamPaperAttempt attempt = new ExamPaperAttempt();
        attempt.setId(41L);
        attempt.setPaperId(7L);
        attempt.setUserId(9L);
        attempt.setAttemptNo(1);
        attempt.setStatus(ExamPaperAttempt.Status.IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.of(2026, 7, 15, 9, 0));
        attempt.setDeadlineAt(LocalDateTime.of(2026, 7, 15, 12, 0));
        attempt.setQuestionCount(3);
        attempt.setAnsweredCount(2);
        return attempt;
    }

    private ExamPaperQuestion paperQuestion(Long id, Long questionId, String type, String answerJson) {
        ExamPaperQuestion question = new ExamPaperQuestion();
        question.setId(id);
        question.setPaperId(7L);
        question.setQuestionId(questionId);
        question.setSortOrder(id.intValue());
        question.setScore(BigDecimal.TEN);
        question.setType(type);
        question.setStem("题目 " + id);
        question.setBodyJson("{}");
        question.setAnswerJson(answerJson);
        question.setScoringJson("{}");
        return question;
    }

    private ExamPaperAttemptAnswer answer(Long paperQuestionId, String answerJson) {
        ExamPaperAttemptAnswer answer = new ExamPaperAttemptAnswer();
        answer.setAttemptId(41L);
        answer.setPaperQuestionId(paperQuestionId);
        answer.setAnswerJson(answerJson);
        answer.setAnswered(true);
        answer.setVersion(1L);
        return answer;
    }

    private ExamQuestion sourceQuestion(Long id, String difficulty, String knowledgePointsJson) {
        ExamQuestion question = new ExamQuestion();
        question.setId(id);
        question.setDifficulty(difficulty);
        question.setKnowledgePointsJson(knowledgePointsJson);
        return question;
    }

    private LearningPathDTO.MasteryView mastery(String key, String score, String status) {
        LearningPathDTO.MasteryView mastery = new LearningPathDTO.MasteryView();
        mastery.setKnowledgePointKey(key);
        mastery.setScore(new BigDecimal(score));
        mastery.setStatus(status);
        mastery.setAttemptCount(1);
        return mastery;
    }

    private LearningPathDTO.PathView path(Long id, int version) {
        LearningPathDTO.PathView path = new LearningPathDTO.PathView();
        path.setId(id);
        path.setUserId(9L);
        path.setCourseKey("python");
        path.setGoal("补齐 Python 基础");
        path.setVersion(version);
        path.setStatus("active");
        path.setProfileDigest("画像摘要");
        path.setMasteryDigest("掌握度摘要");
        path.setGeneratedAt(LocalDateTime.of(2026, 7, 15, 8, 0));

        LearningPathDTO.PathItemView item = new LearningPathDTO.PathItemView();
        item.setId(id + 100);
        item.setPathId(id);
        item.setItemKey("review-lists");
        item.setKnowledgePoint("python.lists.slicing");
        item.setObjective("掌握列表切片");
        item.setTargetMastery(new BigDecimal("80"));
        item.setPriority(2);
        item.setSequenceNo(1);
        item.setResourceKinds(List.of("document", "quiz"));
        item.setResourceIds(List.of("res_doc"));
        item.setStatus("ready");
        item.setDeliveryStatus("pending");
        item.setRationale("按基础路径学习");
        path.setItems(List.of(item));
        return path;
    }
}
