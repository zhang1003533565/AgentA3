package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperAttempt;
import com.example.appbackend.entity.ExamPaperAttemptAnswer;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperAttemptAnswerRepository;
import com.example.appbackend.repository.ExamPaperAttemptRepository;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppExamServiceImplTest {

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
                userProfileService);
    }

    @Test
    void listPublishedUsesOnlyActivePublishedPapersAndAddsUserAttemptState() {
        ExamPaper paper = paper(true);
        ExamPaperAttempt active = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        when(paperRepository.findAppVisible(eq(1), eq(9L), any()))
                .thenReturn(new PageImpl<>(List.of(paper)));
        when(attemptRepository.countByPaperIdAndUserIdAndStatusIn(7L, 9L, completedStatuses()))
                .thenReturn(2L);
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1))
                .thenReturn(Optional.of(active));

        var result = service.listPublished(9L, 0, 20, "  ");

        assertEquals(1, result.getTotalElements());
        assertEquals(2, result.getContent().getFirst().getAttemptCount());
        assertEquals(41L, result.getContent().getFirst().getInProgressAttemptId());
        verify(paperRepository, never()).findAppVisibleByTitle(anyInt(), anyLong(), anyString(), any());
    }

    @Test
    void listPublishedAppliesTrimmedKeyword() {
        when(paperRepository.findAppVisibleByTitle(
                eq(1), eq(9L), eq("期末"), any())).thenReturn(new PageImpl<>(List.of()));

        service.listPublished(9L, 1, 10, "  期末  ");

        verify(paperRepository).findAppVisibleByTitle(
                eq(1), eq(9L), eq("期末"), eq(PageRequest.of(1, 10)));
    }

    @Test
    void paperDetailReturnsMetadataWithoutQuestionAnswers() {
        ExamPaper paper = paper(true);
        when(paperRepository.findByIdAndStatus(7L, 1)).thenReturn(Optional.of(paper));
        when(attemptRepository.countByPaperIdAndUserIdAndStatusIn(7L, 9L, completedStatuses())).thenReturn(1L);
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1)).thenReturn(Optional.empty());

        AppExamDTO.PaperDetail result = service.paperDetail(7L, 9L);

        assertEquals("须知", result.getPrecautions());
        assertEquals(1, result.getAttemptCount());
    }

    @Test
    void unpublishedPaperRejectsNewAttempt() {
        ExamPaper paper = paper(false);
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1)).thenReturn(Optional.empty());
        when(paperRepository.findByIdAndStatusForUpdate(7L, 1)).thenReturn(Optional.of(paper));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.startOrResume(7L, 9L, LocalDateTime.of(2026, 7, 12, 10, 0)));

        assertEquals("试卷已下架", error.getMessage());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void unpublishedPaperStillResumesExistingAttempt() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt active = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        active.setDeadlineAt(now.plusMinutes(5));
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1)).thenReturn(Optional.of(active));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question()));
        when(answerRepository.findByAttemptId(41L)).thenReturn(List.of());

        AppExamDTO.AttemptDetail result = service.startOrResume(7L, 9L, now);

        assertEquals(41L, result.getId());
        verify(paperRepository, never()).findByIdAndStatusForUpdate(anyLong(), anyInt());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void createsAttemptWithServerDeadlineAndNextMonotonicAttemptNumber() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1)).thenReturn(Optional.empty());
        when(paperRepository.findByIdAndStatusForUpdate(7L, 1)).thenReturn(Optional.of(paper(true)));
        when(attemptRepository.findMaxAttemptNoByPaperIdAndUserId(7L, 9L)).thenReturn(2);
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            ExamPaperAttempt saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question()));
        when(answerRepository.findByAttemptId(50L)).thenReturn(List.of());

        AppExamDTO.AttemptDetail result = service.startOrResume(7L, 9L, now);

        ArgumentCaptor<ExamPaperAttempt> captor = ArgumentCaptor.forClass(ExamPaperAttempt.class);
        verify(attemptRepository).save(captor.capture());
        assertEquals(3, captor.getValue().getAttemptNo());
        assertEquals(now.plusMinutes(60), captor.getValue().getDeadlineAt());
        assertEquals(1, result.getQuestions().size());
    }

    @Test
    void expiredAttemptIsAutoSubmittedBeforeCreatingReplacement() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt expired = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        expired.setDeadlineAt(now.minusSeconds(1));
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1))
                .thenReturn(Optional.of(expired));
        when(attemptRepository.findActiveForUpdate(7L, 9L, 1)).thenReturn(Optional.of(expired));
        when(paperRepository.findByIdAndStatusForUpdate(7L, 1)).thenReturn(Optional.of(paper(true)));
        when(attemptRepository.findMaxAttemptNoByPaperIdAndUserId(7L, 9L)).thenReturn(1);
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            ExamPaperAttempt saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(50L);
            return saved;
        });
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question()));
        when(answerRepository.findByAttemptId(41L)).thenReturn(List.of());
        when(answerRepository.findByAttemptId(50L)).thenReturn(List.of());

        service.startOrResume(7L, 9L, now);

        assertEquals(ExamPaperAttempt.Status.AUTO_SUBMITTED, expired.getStatus());
        assertEquals(now, expired.getSubmittedAt());
        assertNull(expired.getActiveMarker());
        InOrder order = inOrder(attemptRepository);
        order.verify(attemptRepository).save(expired);
        order.verify(attemptRepository).save(argThat(value -> value != expired));
    }

    @Test
    void lockRecheckReturnsAttemptCreatedByConcurrentStarter() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt winner = attempt(88L, ExamPaperAttempt.Status.IN_PROGRESS);
        winner.setDeadlineAt(now.plusMinutes(30));
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1))
                .thenReturn(Optional.empty());
        when(attemptRepository.findActiveForUpdate(7L, 9L, 1)).thenReturn(Optional.of(winner));
        when(paperRepository.findByIdAndStatusForUpdate(7L, 1)).thenReturn(Optional.of(paper(true)));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question()));
        when(answerRepository.findByAttemptId(88L)).thenReturn(List.of());

        AppExamDTO.AttemptDetail result = service.startOrResume(7L, 9L, now);

        assertEquals(88L, result.getId());
        verify(attemptRepository).findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1);
        verify(attemptRepository).findActiveForUpdate(7L, 9L, 1);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void attemptDetailRejectsOtherUsersAndDoesNotLeakAnswerMaterial() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        when(attemptRepository.findByIdAndUserId(41L, 9L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.attemptDetail(41L, 9L, now));

        ExamPaperAttempt owned = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        owned.setDeadlineAt(now.plusMinutes(10));
        ExamPaperQuestion question = question();
        ExamPaperAttemptAnswer answer = new ExamPaperAttemptAnswer();
        answer.setPaperQuestionId(question.getId());
        answer.setAnswerJson("{\"selected\":\"B\"}");
        answer.setVersion(3L);
        answer.setAnswered(true);
        when(attemptRepository.findByIdAndUserId(41L, 9L)).thenReturn(Optional.of(owned));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question));
        when(answerRepository.findByAttemptId(41L)).thenReturn(List.of(answer));

        AppExamDTO.QuestionForAttempt safe = service.attemptDetail(41L, 9L, now).getQuestions().getFirst();

        assertEquals("{\"selected\":\"B\"}", safe.getUserAnswerJson());
        assertFalse(safe.getBodyJson().contains("正确"));
        assertFalse(List.of(AppExamDTO.QuestionForAttempt.class.getDeclaredFields()).stream()
                .anyMatch(field -> field.getName().equals("answerJson")));
    }

    @Test
    void saveAnswerChecksOwnershipQuestionMembershipAndVersion() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        AppExamDTO.SaveAnswerRequest request = saveRequest("{\"selectedOption\":\"B\"}", 0L);
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.saveAnswer(41L, 101L, 9L, request, now));

        ExamPaperAttempt owned = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        owned.setDeadlineAt(now.plusMinutes(5));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(owned));
        when(paperQuestionRepository.findByIdAndPaperId(101L, 7L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.saveAnswer(41L, 101L, 9L, request, now));

        ExamPaperAttemptAnswer stored = savedAnswer(101L, "{\"selectedOption\":\"A\"}", 3L, true);
        when(paperQuestionRepository.findByIdAndPaperId(101L, 7L)).thenReturn(Optional.of(question()));
        when(answerRepository.findByAttemptIdAndPaperQuestionId(41L, 101L)).thenReturn(Optional.of(stored));
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.saveAnswer(41L, 101L, 9L, request, now));
        assertEquals(409, conflict.getCode());
    }

    @Test
    void saveAnswerRejectsOversizedPayloadAndIndividualText() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt owned = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        owned.setDeadlineAt(now.plusMinutes(5));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(owned));
        when(paperQuestionRepository.findByIdAndPaperId(101L, 7L)).thenReturn(Optional.of(question()));

        assertThrows(BusinessException.class, () -> service.saveAnswer(
                41L, 101L, 9L, saveRequest("{\"selectedOption\":\"" + "A".repeat(70_000) + "\"}", 0L), now));

        ExamPaperQuestion shortQuestion = question(101L, "short_answer", BigDecimal.TEN,
                "{\"referenceAnswer\":\"参考\"}");
        when(paperQuestionRepository.findByIdAndPaperId(101L, 7L)).thenReturn(Optional.of(shortQuestion));
        assertThrows(BusinessException.class, () -> service.saveAnswer(
                41L, 101L, 9L, saveRequest("{\"text\":\"" + "字".repeat(20_001) + "\"}", 0L), now));
    }

    @Test
    void saveAnswerPersistsNormalizedStateAndAnsweredCount() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt owned = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        owned.setDeadlineAt(now.plusMinutes(5));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(owned));
        when(paperQuestionRepository.findByIdAndPaperId(101L, 7L)).thenReturn(Optional.of(question()));
        when(answerRepository.findByAttemptIdAndPaperQuestionId(41L, 101L)).thenReturn(Optional.empty());
        when(answerRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ExamPaperAttemptAnswer saved = invocation.getArgument(0);
            saved.setVersion(0L);
            return saved;
        });

        AppExamDTO.SavedAnswer result = service.saveAnswer(
                41L, 101L, 9L, saveRequest("{\"selectedOption\":\"B\"}", 0L), now);

        assertTrue(result.getAnswered());
        assertEquals(0L, result.getVersion());
        assertEquals(1, owned.getAnsweredCount());
        verify(attemptRepository).save(owned);
    }

    @Test
    void saveAnswerAcceptsCalculationAndProgrammingText() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt owned = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        owned.setDeadlineAt(now.plusMinutes(5));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(owned));
        when(paperQuestionRepository.findByIdAndPaperId(106L, 7L)).thenReturn(Optional.of(
                question(106L, "calculation", new BigDecimal("12"), "{\"referenceAnswer\":\"过程\"}")));
        when(paperQuestionRepository.findByIdAndPaperId(107L, 7L)).thenReturn(Optional.of(
                question(107L, "programming", new BigDecimal("15"), "{\"referenceAnswer\":\"代码\"}")));
        when(answerRepository.findByAttemptIdAndPaperQuestionId(eq(41L), anyLong())).thenReturn(Optional.empty());
        when(answerRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ExamPaperAttemptAnswer saved = invocation.getArgument(0);
            saved.setVersion(0L);
            return saved;
        });

        AppExamDTO.SavedAnswer calculation = service.saveAnswer(
                41L, 106L, 9L, saveRequest("{\"text\":\"完整计算过程\"}", 0L), now);
        AppExamDTO.SavedAnswer programming = service.saveAnswer(
                41L, 107L, 9L, saveRequest("{\"text\":\"print(1)\"}", 0L), now);

        assertTrue(calculation.getAnswered());
        assertTrue(programming.getAnswered());
        assertEquals(2, owned.getAnsweredCount());
    }

    @Test
    void saveAnswerRejectsWrongShapeAndDatabaseOptimisticConflict() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt owned = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        owned.setDeadlineAt(now.plusMinutes(5));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(owned));
        when(paperQuestionRepository.findByIdAndPaperId(101L, 7L)).thenReturn(Optional.of(question()));

        assertThrows(BusinessException.class, () -> service.saveAnswer(
                41L, 101L, 9L, saveRequest("{\"selectedOptions\":[\"B\"]}", 0L), now));

        when(answerRepository.findByAttemptIdAndPaperQuestionId(41L, 101L)).thenReturn(Optional.empty());
        when(answerRepository.saveAndFlush(any())).thenThrow(
                new ObjectOptimisticLockingFailureException(ExamPaperAttemptAnswer.class, 101L));
        BusinessException conflict = assertThrows(BusinessException.class, () -> service.saveAnswer(
                41L, 101L, 9L, saveRequest("{\"selectedOption\":\"B\"}", 0L), now));
        assertEquals(409, conflict.getCode());
    }

    @Test
    void expiredSaveAutoSubmitsThroughTheSharedScoringPath() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt expired = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        expired.setDeadlineAt(now);
        ExamPaperQuestion single = question(101L, "single_choice", new BigDecimal("2"),
                "{\"correctOption\":\"B\"}");
        ExamPaperAttemptAnswer answer = savedAnswer(101L, "{\"selectedOption\":\"B\"}", 1L, true);
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(expired));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(single));
        when(answerRepository.findByAttemptId(41L)).thenReturn(List.of(answer));

        BusinessException ended = assertThrows(BusinessException.class, () -> service.saveAnswer(
                41L, 101L, 9L, saveRequest("{\"selectedOption\":\"A\"}", 1L), now));

        assertEquals("答题已结束", ended.getMessage());
        assertEquals(ExamPaperAttempt.Status.AUTO_SUBMITTED, expired.getStatus());
        assertEquals(new BigDecimal("2"), expired.getObjectiveScore());
        assertEquals(new BigDecimal("2"), expired.getObjectiveTotalScore());
    }

    @Test
    void submitScoresAllSevenTypesAndExcludesManualAnswersFromObjectiveTotal() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt active = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        active.setDeadlineAt(now.plusMinutes(1));
        List<ExamPaperQuestion> questions = List.of(
                question(101L, "single_choice", new BigDecimal("2"), "{\"correctOption\":\"B\"}"),
                question(102L, "multiple_choice", new BigDecimal("3"), "{\"correctOptions\":[\"A\",\"C\"]}"),
                question(103L, "true_false", BigDecimal.ONE, "{\"correct\":true}"),
                question(104L, "fill_blank", new BigDecimal("4"),
                        "{\"blanks\":[{\"id\":\"b1\",\"answers\":[\"栈顶\"]},{\"id\":\"b2\",\"answers\":[\"栈底\"]}]}"),
                question(105L, "short_answer", new BigDecimal("10"), "{\"referenceAnswer\":\"略\"}"),
                question(106L, "calculation", new BigDecimal("12"), "{\"referenceAnswer\":\"计算过程\"}"),
                question(107L, "programming", new BigDecimal("15"), "{\"referenceAnswer\":\"参考代码\"}"));
        List<ExamPaperAttemptAnswer> answers = List.of(
                savedAnswer(101L, "{\"selectedOption\":\"B\"}", 1L, true),
                savedAnswer(102L, "{\"selectedOptions\":[\"C\",\"A\",\"C\"]}", 1L, true),
                savedAnswer(103L, "{\"value\":false}", 1L, true),
                savedAnswer(104L, "{\"blanks\":[{\"id\":\"b2\",\"value\":\" 栈底 \"},{\"id\":\"b1\",\"value\":\"栈顶\"}]}", 1L, true),
                savedAnswer(105L, "{\"text\":\"我的说明\"}", 1L, true),
                savedAnswer(106L, "{\"text\":\"我的计算过程\"}", 1L, true),
                savedAnswer(107L, "{\"text\":\"print(1)\"}", 1L, true));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(active));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(questions);
        when(answerRepository.findByAttemptId(41L)).thenReturn(answers);

        AppExamDTO.AttemptResult result = service.submit(41L, 9L, now);

        assertEquals(ExamPaperAttempt.Status.SUBMITTED, active.getStatus());
        assertEquals(new BigDecimal("9"), result.getObjectiveScore());
        assertEquals(new BigDecimal("10"), result.getObjectiveTotalScore());
        assertEquals(java.util.Arrays.asList(true, true, false, true, null, null, null),
                answers.stream().map(ExamPaperAttemptAnswer::getCorrect).toList());
        assertNull(answers.get(4).getScore());
        assertNull(answers.get(5).getScore());
        assertNull(answers.get(6).getScore());
    }

    @Test
    void repeatedSubmitIsIdempotentAndResultRequiresSubmittedOwnedAttempt() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 10, 0);
        ExamPaperAttempt submitted = attempt(41L, ExamPaperAttempt.Status.SUBMITTED);
        submitted.setSubmittedAt(now.minusMinutes(1));
        submitted.setObjectiveScore(new BigDecimal("5"));
        submitted.setObjectiveTotalScore(new BigDecimal("6"));
        when(attemptRepository.findByIdAndUserId(41L, 9L)).thenReturn(Optional.of(submitted));
        when(attemptRepository.findByIdAndUserIdForUpdate(41L, 9L)).thenReturn(Optional.of(submitted));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(answerRepository.findByAttemptId(41L)).thenReturn(List.of());

        assertEquals(new BigDecimal("5"), service.submit(41L, 9L, now).getObjectiveScore());
        assertEquals(new BigDecimal("5"), service.result(41L, 9L).getObjectiveScore());
        verify(attemptRepository, never()).save(any());
        verify(answerRepository, never()).saveAll(any());

        when(attemptRepository.findByIdAndUserId(42L, 9L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.result(42L, 9L));
    }

    @Test
    void historyUsesOnlyCompletedStatusesInSubmittedOrder() {
        ExamPaperAttempt latest = attempt(42L, ExamPaperAttempt.Status.AUTO_SUBMITTED);
        latest.setSubmittedAt(LocalDateTime.of(2026, 7, 12, 11, 0));
        when(attemptRepository.findByPaperIdAndUserIdAndStatusInOrderBySubmittedAtDesc(
                7L, 9L, completedStatuses())).thenReturn(List.of(latest));

        List<AppExamDTO.AttemptHistoryItem> result = service.history(7L, 9L);

        assertEquals(List.of(42L), result.stream().map(AppExamDTO.AttemptHistoryItem::getId).toList());
    }

    private ExamPaper paper(boolean published) {
        ExamPaper paper = new ExamPaper();
        paper.setId(7L);
        paper.setTitle("期末考试");
        paper.setSubtitle("第一学期");
        paper.setDurationMinutes(60);
        paper.setPrecautions("须知");
        paper.setQuestionCount(1);
        paper.setTotalScore(BigDecimal.TEN);
        paper.setPublishTime(LocalDateTime.of(2026, 7, 1, 8, 0));
        paper.setPublished(published);
        paper.setStatus(1);
        return paper;
    }

    private List<ExamPaperAttempt.Status> completedStatuses() {
        return List.of(ExamPaperAttempt.Status.SUBMITTED, ExamPaperAttempt.Status.AUTO_SUBMITTED);
    }

    private AppExamDTO.SaveAnswerRequest saveRequest(String answerJson, long version) {
        AppExamDTO.SaveAnswerRequest request = new AppExamDTO.SaveAnswerRequest();
        request.setAnswerJson(answerJson);
        request.setVersion(version);
        return request;
    }

    private ExamPaperAttemptAnswer savedAnswer(Long paperQuestionId, String answerJson, Long version, boolean answered) {
        ExamPaperAttemptAnswer answer = new ExamPaperAttemptAnswer();
        answer.setAttemptId(41L);
        answer.setPaperQuestionId(paperQuestionId);
        answer.setAnswerJson(answerJson);
        answer.setVersion(version);
        answer.setAnswered(answered);
        return answer;
    }

    private ExamPaperQuestion question(Long id, String type, BigDecimal score, String answerJson) {
        ExamPaperQuestion question = question();
        question.setId(id);
        question.setType(type);
        question.setScore(score);
        question.setAnswerJson(answerJson);
        return question;
    }

    private ExamPaperAttempt attempt(Long id, ExamPaperAttempt.Status status) {
        ExamPaperAttempt attempt = new ExamPaperAttempt();
        attempt.setId(id);
        attempt.setPaperId(7L);
        attempt.setUserId(9L);
        attempt.setAttemptNo(1);
        attempt.setStatus(status);
        attempt.setStartedAt(LocalDateTime.of(2026, 7, 12, 9, 0));
        attempt.setDeadlineAt(LocalDateTime.of(2026, 7, 12, 11, 0));
        attempt.setAnsweredCount(0);
        attempt.setQuestionCount(1);
        return attempt;
    }

    private ExamPaperQuestion question() {
        ExamPaperQuestion question = new ExamPaperQuestion();
        question.setId(101L);
        question.setPaperId(7L);
        question.setQuestionId(501L);
        question.setSortOrder(1);
        question.setSectionOrder(1);
        question.setScore(BigDecimal.TEN);
        question.setType("single_choice");
        question.setStem("1+1=?");
        question.setBodyJson("{\"options\":[{\"key\":\"A\",\"text\":\"1\"},{\"key\":\"B\",\"text\":\"2\"}]}");
        question.setAnswerJson("{\"selected\":\"B\"}");
        question.setAnalysis("正确答案是 B");
        question.setScoringJson("{\"mode\":\"exact\"}");
        return question;
    }
}
