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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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

    private AppExamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppExamServiceImpl(paperRepository, paperQuestionRepository, attemptRepository, answerRepository);
    }

    @Test
    void listPublishedUsesOnlyActivePublishedPapersAndAddsUserAttemptState() {
        ExamPaper paper = paper(true);
        ExamPaperAttempt active = attempt(41L, ExamPaperAttempt.Status.IN_PROGRESS);
        when(paperRepository.findByPublishedTrueAndStatusOrderByPublishTimeDesc(eq(1), any()))
                .thenReturn(new PageImpl<>(List.of(paper)));
        when(attemptRepository.countByPaperIdAndUserIdAndStatusIn(7L, 9L, completedStatuses()))
                .thenReturn(2L);
        when(attemptRepository.findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1))
                .thenReturn(Optional.of(active));

        var result = service.listPublished(9L, 0, 20, "  ");

        assertEquals(1, result.getTotalElements());
        assertEquals(2, result.getContent().getFirst().getAttemptCount());
        assertEquals(41L, result.getContent().getFirst().getInProgressAttemptId());
        verify(paperRepository, never()).findByPublishedTrueAndStatusAndTitleContainingOrderByPublishTimeDesc(
                anyInt(), anyString(), any());
    }

    @Test
    void listPublishedAppliesTrimmedKeyword() {
        when(paperRepository.findByPublishedTrueAndStatusAndTitleContainingOrderByPublishTimeDesc(
                eq(1), eq("期末"), any())).thenReturn(new PageImpl<>(List.of()));

        service.listPublished(9L, 1, 10, "  期末  ");

        verify(paperRepository).findByPublishedTrueAndStatusAndTitleContainingOrderByPublishTimeDesc(
                eq(1), eq("期末"), eq(PageRequest.of(1, 10)));
    }

    @Test
    void paperDetailReturnsMetadataWithoutQuestionAnswers() {
        ExamPaper paper = paper(true);
        when(paperRepository.findByIdAndStatusAndPublishedTrue(7L, 1)).thenReturn(Optional.of(paper));
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
        when(paperRepository.findByIdAndStatusForUpdate(7L, 1)).thenReturn(Optional.of(paper(true)));
        when(attemptRepository.findMaxAttemptNoByPaperIdAndUserId(7L, 9L)).thenReturn(1);
        when(attemptRepository.save(any())).thenAnswer(invocation -> {
            ExamPaperAttempt saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(50L);
            return saved;
        });
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question()));
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
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(paperRepository.findByIdAndStatusForUpdate(7L, 1)).thenReturn(Optional.of(paper(true)));
        when(paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of(question()));
        when(answerRepository.findByAttemptId(88L)).thenReturn(List.of());

        AppExamDTO.AttemptDetail result = service.startOrResume(7L, 9L, now);

        assertEquals(88L, result.getId());
        verify(attemptRepository, times(2)).findByPaperIdAndUserIdAndActiveMarker(7L, 9L, 1);
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
