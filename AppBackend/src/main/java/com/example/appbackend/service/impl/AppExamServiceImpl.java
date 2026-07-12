package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperAttempt;
import com.example.appbackend.entity.ExamPaperAttemptAnswer;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperAttemptAnswerRepository;
import com.example.appbackend.repository.ExamPaperAttemptRepository;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.service.AppExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppExamServiceImpl implements AppExamService {
    private static final Collection<ExamPaperAttempt.Status> COMPLETED_STATUSES = List.of(
            ExamPaperAttempt.Status.SUBMITTED, ExamPaperAttempt.Status.AUTO_SUBMITTED);

    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final ExamPaperAttemptRepository attemptRepository;
    private final ExamPaperAttemptAnswerRepository answerRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AppExamDTO.PaperSummary> listPublished(Long userId, int page, int size, String keyword) {
        PageRequest pageable = PageRequest.of(page, size);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<ExamPaper> papers = normalizedKeyword.isEmpty()
                ? paperRepository.findByPublishedTrueAndStatusOrderByPublishTimeDesc(1, pageable)
                : paperRepository.findByPublishedTrueAndStatusAndTitleContainingOrderByPublishTimeDesc(
                        1, normalizedKeyword, pageable);
        return papers.map(paper -> toSummary(paper, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public AppExamDTO.PaperDetail paperDetail(Long paperId, Long userId) {
        ExamPaper paper = paperRepository.findByIdAndStatusAndPublishedTrue(paperId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在或已下架"));
        AppExamDTO.PaperDetail detail = new AppExamDTO.PaperDetail();
        copySummary(toSummary(paper, userId), detail);
        detail.setPrecautions(paper.getPrecautions());
        return detail;
    }

    @Override
    @Transactional
    public AppExamDTO.AttemptDetail startOrResume(Long paperId, Long userId, LocalDateTime now) {
        ExamPaperAttempt active = attemptRepository
                .findByPaperIdAndUserIdAndActiveMarker(paperId, userId, 1)
                .orElse(null);
        if (active != null && active.getDeadlineAt().isAfter(now)) {
            return toAttemptDetail(active);
        }

        ExamPaper paper = paperRepository.findByIdAndStatusForUpdate(paperId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在"));
        active = attemptRepository.findByPaperIdAndUserIdAndActiveMarker(paperId, userId, 1)
                .orElse(null);
        if (active != null && active.getDeadlineAt().isAfter(now)) {
            return toAttemptDetail(active);
        }
        if (active != null) {
            autoSubmit(active, now);
        }
        if (!Boolean.TRUE.equals(paper.getPublished())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "试卷已下架");
        }

        ExamPaperAttempt created = new ExamPaperAttempt();
        created.setPaperId(paperId);
        created.setUserId(userId);
        created.setAttemptNo(attemptRepository.findMaxAttemptNoByPaperIdAndUserId(paperId, userId) + 1);
        created.setStatus(ExamPaperAttempt.Status.IN_PROGRESS);
        created.setActiveMarker(1);
        created.setStartedAt(now);
        created.setDeadlineAt(now.plusMinutes(paper.getDurationMinutes()));
        created.setQuestionCount(paper.getQuestionCount());
        created = attemptRepository.save(created);
        return toAttemptDetail(created);
    }

    @Override
    @Transactional
    public AppExamDTO.AttemptDetail attemptDetail(Long attemptId, Long userId, LocalDateTime now) {
        ExamPaperAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "答题记录不存在"));
        if (attempt.getStatus() == ExamPaperAttempt.Status.IN_PROGRESS
                && !attempt.getDeadlineAt().isAfter(now)) {
            autoSubmit(attempt, now);
        }
        return toAttemptDetail(attempt);
    }

    private AppExamDTO.PaperSummary toSummary(ExamPaper paper, Long userId) {
        AppExamDTO.PaperSummary summary = new AppExamDTO.PaperSummary();
        summary.setId(paper.getId());
        summary.setTitle(paper.getTitle());
        summary.setSubtitle(paper.getSubtitle());
        summary.setDurationMinutes(paper.getDurationMinutes());
        summary.setQuestionCount(paper.getQuestionCount());
        summary.setTotalScore(paper.getTotalScore());
        summary.setPublishTime(paper.getPublishTime());
        summary.setAttemptCount(completedCount(paper.getId(), userId));
        attemptRepository.findByPaperIdAndUserIdAndActiveMarker(paper.getId(), userId, 1)
                .map(ExamPaperAttempt::getId)
                .ifPresent(summary::setInProgressAttemptId);
        return summary;
    }

    private long completedCount(Long paperId, Long userId) {
        return attemptRepository.countByPaperIdAndUserIdAndStatusIn(paperId, userId, COMPLETED_STATUSES);
    }

    private AppExamDTO.AttemptDetail toAttemptDetail(ExamPaperAttempt attempt) {
        List<ExamPaperQuestion> questions = paperQuestionRepository
                .findByPaperIdOrderBySortOrderAscIdAsc(attempt.getPaperId());
        Map<Long, ExamPaperAttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(ExamPaperAttemptAnswer::getPaperQuestionId, Function.identity()));

        AppExamDTO.AttemptDetail detail = new AppExamDTO.AttemptDetail();
        detail.setId(attempt.getId());
        detail.setPaperId(attempt.getPaperId());
        detail.setAttemptNo(attempt.getAttemptNo());
        detail.setStatus(attempt.getStatus());
        detail.setStartedAt(attempt.getStartedAt());
        detail.setDeadlineAt(attempt.getDeadlineAt());
        detail.setSubmittedAt(attempt.getSubmittedAt());
        detail.setAnsweredCount(attempt.getAnsweredCount());
        detail.setQuestionCount(attempt.getQuestionCount());
        detail.setQuestions(questions.stream().map(question -> toQuestion(question, answers.get(question.getId()))).toList());
        return detail;
    }

    private AppExamDTO.QuestionForAttempt toQuestion(
            ExamPaperQuestion question, ExamPaperAttemptAnswer answer) {
        AppExamDTO.QuestionForAttempt result = new AppExamDTO.QuestionForAttempt();
        result.setId(question.getId());
        result.setQuestionId(question.getQuestionId());
        result.setSortOrder(question.getSortOrder());
        result.setSectionOrder(question.getSectionOrder());
        result.setScore(question.getScore());
        result.setType(question.getType());
        result.setStem(question.getStem());
        result.setBodyJson(question.getBodyJson());
        if (answer == null) {
            result.setVersion(0L);
            result.setAnswered(false);
        } else {
            result.setUserAnswerJson(answer.getAnswerJson());
            result.setVersion(answer.getVersion());
            result.setAnswered(answer.getAnswered());
        }
        return result;
    }

    private void autoSubmit(ExamPaperAttempt attempt, LocalDateTime now) {
        attempt.setStatus(ExamPaperAttempt.Status.AUTO_SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setActiveMarker(null);
        attemptRepository.save(attempt);
    }

    private void copySummary(AppExamDTO.PaperSummary source, AppExamDTO.PaperSummary target) {
        target.setId(source.getId());
        target.setTitle(source.getTitle());
        target.setSubtitle(source.getSubtitle());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setQuestionCount(source.getQuestionCount());
        target.setTotalScore(source.getTotalScore());
        target.setPublishTime(source.getPublishTime());
        target.setAttemptCount(source.getAttemptCount());
        target.setInProgressAttemptId(source.getInProgressAttemptId());
    }
}
