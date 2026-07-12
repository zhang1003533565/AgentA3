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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppExamServiceImpl implements AppExamService {
    private static final int MAX_ANSWER_BYTES = 64 * 1024;
    private static final int MAX_TEXT_LENGTH = 20_000;
    private static final Collection<ExamPaperAttempt.Status> COMPLETED_STATUSES = List.of(
            ExamPaperAttempt.Status.SUBMITTED, ExamPaperAttempt.Status.AUTO_SUBMITTED);

    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final ExamPaperAttemptRepository attemptRepository;
    private final ExamPaperAttemptAnswerRepository answerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    @Transactional
    public AppExamDTO.SavedAnswer saveAnswer(Long attemptId, Long paperQuestionId, Long userId,
                                             AppExamDTO.SaveAnswerRequest request, LocalDateTime now) {
        ExamPaperAttempt attempt = ownedAttempt(attemptId, userId);
        if (attempt.getStatus() != ExamPaperAttempt.Status.IN_PROGRESS) {
            throw new BusinessException(409, "答题已结束");
        }
        if (!attempt.getDeadlineAt().isAfter(now)) {
            scoreAndSubmit(attempt, ExamPaperAttempt.Status.AUTO_SUBMITTED, now);
            throw new BusinessException(409, "答题已结束");
        }
        ExamPaperQuestion question = paperQuestionRepository.findByIdAndPaperId(paperQuestionId, attempt.getPaperId())
                .orElseThrow(() -> new BusinessException(Result.BAD_REQUEST_CODE, "题目不属于该试卷"));
        JsonNode answerNode = validateAnswer(question.getType(), request.getAnswerJson());

        ExamPaperAttemptAnswer answer = answerRepository
                .findByAttemptIdAndPaperQuestionId(attemptId, paperQuestionId)
                .orElse(null);
        long expectedVersion = answer == null || answer.getVersion() == null ? 0L : answer.getVersion();
        if (!Objects.equals(request.getVersion(), expectedVersion)) {
            throw new BusinessException(409, "答案版本冲突");
        }
        boolean wasAnswered = answer != null && Boolean.TRUE.equals(answer.getAnswered());
        if (answer == null) {
            answer = new ExamPaperAttemptAnswer();
            answer.setAttemptId(attemptId);
            answer.setPaperQuestionId(paperQuestionId);
        }
        boolean answered = isAnswered(question.getType(), answerNode);
        answer.setAnswerJson(request.getAnswerJson());
        answer.setAnswered(answered);
        answer.setCorrect(null);
        answer.setScore(null);
        try {
            answer = answerRepository.saveAndFlush(answer);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new BusinessException(409, "答案版本冲突");
        }
        if (wasAnswered != answered) {
            attempt.setAnsweredCount(Math.max(0, attempt.getAnsweredCount() + (answered ? 1 : -1)));
            attemptRepository.save(attempt);
        }
        AppExamDTO.SavedAnswer saved = new AppExamDTO.SavedAnswer();
        saved.setPaperQuestionId(paperQuestionId);
        saved.setAnswerJson(answer.getAnswerJson());
        saved.setVersion(answer.getVersion());
        saved.setAnswered(answer.getAnswered());
        return saved;
    }

    @Override
    @Transactional
    public AppExamDTO.AttemptResult submit(Long attemptId, Long userId, LocalDateTime now) {
        ExamPaperAttempt attempt = ownedAttempt(attemptId, userId);
        if (attempt.getStatus() == ExamPaperAttempt.Status.IN_PROGRESS) {
            ExamPaperAttempt.Status target = attempt.getDeadlineAt().isAfter(now)
                    ? ExamPaperAttempt.Status.SUBMITTED : ExamPaperAttempt.Status.AUTO_SUBMITTED;
            scoreAndSubmit(attempt, target, now);
        }
        return toAttemptResult(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppExamDTO.AttemptHistoryItem> history(Long paperId, Long userId) {
        return attemptRepository.findByPaperIdAndUserIdAndStatusInOrderBySubmittedAtDesc(
                paperId, userId, COMPLETED_STATUSES).stream().map(this::toHistoryItem).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AppExamDTO.AttemptResult result(Long attemptId, Long userId) {
        ExamPaperAttempt attempt = ownedAttempt(attemptId, userId);
        if (attempt.getStatus() == ExamPaperAttempt.Status.IN_PROGRESS) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "答题尚未提交");
        }
        return toAttemptResult(attempt);
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
        scoreAndSubmit(attempt, ExamPaperAttempt.Status.AUTO_SUBMITTED, now);
    }

    private ExamPaperAttempt ownedAttempt(Long attemptId, Long userId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "答题记录不存在"));
    }

    private JsonNode validateAnswer(String type, String answerJson) {
        if (answerJson == null || answerJson.getBytes(StandardCharsets.UTF_8).length > MAX_ANSWER_BYTES) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "答案不能超过 64 KiB");
        }
        final JsonNode node;
        try {
            node = objectMapper.readTree(answerJson);
        } catch (JsonProcessingException invalid) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "答案 JSON 不合法");
        }
        if (node == null || !node.isObject()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "答案结构不合法");
        }
        validateTextLengths(node);
        boolean valid = switch (type) {
            case "single_choice" -> node.path("selectedOption").isTextual();
            case "multiple_choice" -> isTextArray(node.path("selectedOptions"));
            case "true_false" -> node.path("value").isBoolean();
            case "fill_blank" -> isValidBlanks(node.path("blanks"));
            case "short_answer" -> node.path("text").isTextual();
            default -> false;
        };
        if (!valid) throw new BusinessException(Result.BAD_REQUEST_CODE, "答案结构与题型不匹配");
        return node;
    }

    private void checkTextLength(String value) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "单个文本答案不能超过 20000 字符");
        }
    }

    private void validateTextLengths(JsonNode node) {
        if (node.isTextual()) {
            checkTextLength(node.asText());
            return;
        }
        node.elements().forEachRemaining(this::validateTextLengths);
    }

    private boolean isTextArray(JsonNode node) {
        if (!node.isArray()) return false;
        for (JsonNode item : node) if (!item.isTextual()) return false;
        return true;
    }

    private boolean isValidBlanks(JsonNode node) {
        if (!node.isArray()) return false;
        Set<String> ids = new HashSet<>();
        for (JsonNode blank : node) {
            String id = blank.path("id").asText().trim();
            if (!blank.isObject() || !blank.path("id").isTextual() || !blank.path("value").isTextual()
                    || id.isEmpty() || !ids.add(id)) return false;
        }
        return true;
    }

    private boolean isAnswered(String type, JsonNode node) {
        return switch (type) {
            case "single_choice" -> !node.path("selectedOption").asText().trim().isEmpty();
            case "multiple_choice" -> node.path("selectedOptions").size() > 0;
            case "true_false" -> true;
            case "fill_blank" -> {
                boolean any = false;
                for (JsonNode blank : node.path("blanks")) any |= !blank.path("value").asText().trim().isEmpty();
                yield any;
            }
            case "short_answer" -> !node.path("text").asText().trim().isEmpty();
            default -> false;
        };
    }

    private void scoreAndSubmit(ExamPaperAttempt attempt, ExamPaperAttempt.Status status, LocalDateTime now) {
        List<ExamPaperQuestion> questions = paperQuestionRepository
                .findByPaperIdOrderBySortOrderAscIdAsc(attempt.getPaperId());
        Map<Long, ExamPaperAttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(ExamPaperAttemptAnswer::getPaperQuestionId, Function.identity()));
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        List<ExamPaperAttemptAnswer> scoredAnswers = new ArrayList<>();
        for (ExamPaperQuestion question : questions) {
            ExamPaperAttemptAnswer answer = answers.get(question.getId());
            if ("short_answer".equals(question.getType())) {
                if (answer != null) {
                    answer.setCorrect(null);
                    answer.setScore(null);
                    scoredAnswers.add(answer);
                }
                continue;
            }
            total = total.add(question.getScore());
            if (answer == null) continue;
            boolean correct = isCorrect(question, answer.getAnswerJson());
            answer.setCorrect(correct);
            answer.setScore(correct ? question.getScore() : BigDecimal.ZERO);
            if (correct) score = score.add(question.getScore());
            scoredAnswers.add(answer);
        }
        if (!scoredAnswers.isEmpty()) answerRepository.saveAll(scoredAnswers);
        attempt.setObjectiveScore(score);
        attempt.setObjectiveTotalScore(total);
        attempt.setStatus(status);
        attempt.setSubmittedAt(now);
        attempt.setActiveMarker(null);
        attemptRepository.save(attempt);
    }

    private boolean isCorrect(ExamPaperQuestion question, String userAnswerJson) {
        try {
            JsonNode standard = objectMapper.readTree(question.getAnswerJson());
            JsonNode user = objectMapper.readTree(userAnswerJson);
            return switch (question.getType()) {
                case "single_choice" -> clean(user.path("selectedOption").asText())
                        .equals(clean(standard.path("correctOption").asText()));
                case "multiple_choice" -> textSet(user.path("selectedOptions"))
                        .equals(textSet(standard.path("correctOptions")));
                case "true_false" -> user.path("value").isBoolean()
                        && standard.path("correct").isBoolean()
                        && user.path("value").asBoolean() == standard.path("correct").asBoolean();
                case "fill_blank" -> fillAnswersMatch(user.path("blanks"), standard.path("blanks"));
                default -> false;
            };
        } catch (JsonProcessingException | RuntimeException invalid) {
            return false;
        }
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        if (array.isArray()) for (JsonNode item : array) values.add(clean(item.asText()));
        values.remove("");
        return values;
    }

    private boolean fillAnswersMatch(JsonNode userBlanks, JsonNode standardBlanks) {
        if (!userBlanks.isArray() || !standardBlanks.isArray()) return false;
        Map<String, String> user = new HashMap<>();
        for (JsonNode blank : userBlanks) user.put(clean(blank.path("id").asText()), clean(blank.path("value").asText()));
        if (user.size() != standardBlanks.size()) return false;
        for (JsonNode blank : standardBlanks) {
            String actual = user.get(clean(blank.path("id").asText()));
            if (actual == null || !acceptedAnswers(blank.path("answers")).contains(actual)) return false;
        }
        return true;
    }

    private Set<String> acceptedAnswers(JsonNode answers) {
        Set<String> values = new HashSet<>();
        if (answers.isArray()) for (JsonNode answer : answers) values.add(clean(answer.asText()));
        return values;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private AppExamDTO.AttemptHistoryItem toHistoryItem(ExamPaperAttempt attempt) {
        AppExamDTO.AttemptHistoryItem item = new AppExamDTO.AttemptHistoryItem();
        item.setId(attempt.getId());
        item.setAttemptNo(attempt.getAttemptNo());
        item.setStatus(attempt.getStatus());
        item.setStartedAt(attempt.getStartedAt());
        item.setSubmittedAt(attempt.getSubmittedAt());
        item.setObjectiveScore(attempt.getObjectiveScore());
        item.setObjectiveTotalScore(attempt.getObjectiveTotalScore());
        return item;
    }

    private AppExamDTO.AttemptResult toAttemptResult(ExamPaperAttempt attempt) {
        List<ExamPaperQuestion> questions = paperQuestionRepository
                .findByPaperIdOrderBySortOrderAscIdAsc(attempt.getPaperId());
        Map<Long, ExamPaperAttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId()).stream()
                .collect(Collectors.toMap(ExamPaperAttemptAnswer::getPaperQuestionId, Function.identity()));
        AppExamDTO.AttemptResult result = new AppExamDTO.AttemptResult();
        result.setId(attempt.getId());
        result.setPaperId(attempt.getPaperId());
        result.setAttemptNo(attempt.getAttemptNo());
        result.setStatus(attempt.getStatus());
        result.setStartedAt(attempt.getStartedAt());
        result.setSubmittedAt(attempt.getSubmittedAt());
        result.setObjectiveScore(attempt.getObjectiveScore());
        result.setObjectiveTotalScore(attempt.getObjectiveTotalScore());
        result.setQuestions(questions.stream().map(question -> toQuestionResult(question, answers.get(question.getId()))).toList());
        return result;
    }

    private AppExamDTO.QuestionResult toQuestionResult(ExamPaperQuestion question, ExamPaperAttemptAnswer answer) {
        AppExamDTO.QuestionResult result = new AppExamDTO.QuestionResult();
        result.setId(question.getId());
        result.setQuestionId(question.getQuestionId());
        result.setSortOrder(question.getSortOrder());
        result.setMaxScore(question.getScore());
        result.setType(question.getType());
        result.setStem(question.getStem());
        result.setBodyJson(question.getBodyJson());
        result.setAnswerJson(question.getAnswerJson());
        result.setAnalysis(question.getAnalysis());
        result.setScoringJson(question.getScoringJson());
        if (answer != null) {
            result.setUserAnswerJson(answer.getAnswerJson());
            result.setAnswered(answer.getAnswered());
            result.setCorrect(answer.getCorrect());
            result.setScore(answer.getScore());
        } else {
            result.setAnswered(false);
        }
        return result;
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
