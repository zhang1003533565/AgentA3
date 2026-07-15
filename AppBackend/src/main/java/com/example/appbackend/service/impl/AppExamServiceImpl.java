package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.dto.UserProfileDTO;
import com.example.appbackend.entity.ExamPaper;
import com.example.appbackend.entity.ExamPaperAttempt;
import com.example.appbackend.entity.ExamPaperAttemptAnswer;
import com.example.appbackend.entity.ExamPaperQuestion;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamPaperAttemptAnswerRepository;
import com.example.appbackend.repository.ExamPaperAttemptRepository;
import com.example.appbackend.repository.ExamPaperQuestionRepository;
import com.example.appbackend.repository.ExamPaperRepository;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.AppExamService;
import com.example.appbackend.service.LearningPathService;
import com.example.appbackend.service.UserProfileService;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppExamServiceImpl implements AppExamService {
    private static final int MAX_ANSWER_BYTES = 64 * 1024;
    private static final int MAX_TEXT_LENGTH = 20_000;
    private static final String PYTHON = "python";
    private static final Pattern PYTHON_KNOWLEDGE_POINT = Pattern.compile(
            "python(?:\\.[a-z0-9_-]+)+");
    private static final Collection<ExamPaperAttempt.Status> COMPLETED_STATUSES = List.of(
            ExamPaperAttempt.Status.SUBMITTED, ExamPaperAttempt.Status.AUTO_SUBMITTED);

    private final ExamPaperRepository paperRepository;
    private final ExamPaperQuestionRepository paperQuestionRepository;
    private final ExamPaperAttemptRepository attemptRepository;
    private final ExamPaperAttemptAnswerRepository answerRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final LearningPathService learningPathService;
    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public Page<AppExamDTO.PaperSummary> listPublished(Long userId, int page, int size, String keyword) {
        PageRequest pageable = PageRequest.of(page, size);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Page<ExamPaper> papers = normalizedKeyword.isEmpty()
                ? paperRepository.findAppVisible(1, userId, pageable)
                : paperRepository.findAppVisibleByTitle(1, userId, normalizedKeyword, pageable);
        return papers.map(paper -> toSummary(paper, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public AppExamDTO.PaperDetail paperDetail(Long paperId, Long userId) {
        ExamPaper paper = paperRepository.findByIdAndStatus(paperId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在"));
        if (!Boolean.TRUE.equals(paper.getPublished())
                && !attemptRepository.existsByPaperIdAndUserId(paperId, userId)) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在或已下架");
        }
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
            return toAttemptDetail(active, now);
        }

        ExamPaper paper = paperRepository.findByIdAndStatusForUpdate(paperId, 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "试卷不存在"));
        active = attemptRepository.findActiveForUpdate(paperId, userId, 1)
                .orElse(null);
        if (active != null && active.getDeadlineAt().isAfter(now)) {
            return toAttemptDetail(active, now);
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
        return toAttemptDetail(created, now);
    }

    @Override
    @Transactional
    public AppExamDTO.AttemptDetail attemptDetail(Long attemptId, Long userId, LocalDateTime now) {
        ExamPaperAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "答题记录不存在"));
        if (attempt.getStatus() == ExamPaperAttempt.Status.IN_PROGRESS
                && !attempt.getDeadlineAt().isAfter(now)) {
            attempt = ownedAttemptForUpdate(attemptId, userId);
            if (attempt.getStatus() == ExamPaperAttempt.Status.IN_PROGRESS
                    && !attempt.getDeadlineAt().isAfter(now)) {
                autoSubmit(attempt, now);
            }
        }
        return toAttemptDetail(attempt, now);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AppExamDTO.SavedAnswer saveAnswer(Long attemptId, Long paperQuestionId, Long userId,
                                             AppExamDTO.SaveAnswerRequest request, LocalDateTime now) {
        ExamPaperAttempt attempt = ownedAttemptForUpdate(attemptId, userId);
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
        ExamPaperAttempt attempt = ownedAttemptForUpdate(attemptId, userId);
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

    private AppExamDTO.AttemptDetail toAttemptDetail(ExamPaperAttempt attempt, LocalDateTime serverNow) {
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
        detail.setServerNow(serverNow);
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

    private ExamPaperAttempt ownedAttemptForUpdate(Long attemptId, Long userId) {
        return attemptRepository.findByIdAndUserIdForUpdate(attemptId, userId)
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
        AppExamDTO.LearningUpdate learningUpdate = applyLearningFeedback(
                attempt, questions, answers, now);
        if (learningUpdate != null) {
            attempt.setLearningUpdateJson(writeLearningUpdate(learningUpdate));
        }
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
        result.setLearningUpdate(readLearningUpdate(attempt.getLearningUpdateJson()));
        return result;
    }

    private AppExamDTO.LearningUpdate applyLearningFeedback(
            ExamPaperAttempt attempt,
            List<ExamPaperQuestion> questions,
            Map<Long, ExamPaperAttemptAnswer> answers,
            LocalDateTime now) {
        List<ExamPaperQuestion> eligibleQuestions = questions.stream()
                .filter(question -> !"short_answer".equals(question.getType()))
                .filter(question -> question.getQuestionId() != null)
                .filter(question -> {
                    ExamPaperAttemptAnswer answer = answers.get(question.getId());
                    return answer != null
                            && Boolean.TRUE.equals(answer.getAnswered())
                            && answer.getCorrect() != null;
                })
                .toList();
        if (eligibleQuestions.isEmpty()) return null;

        List<Long> sourceQuestionIds = eligibleQuestions.stream()
                .map(ExamPaperQuestion::getQuestionId)
                .distinct()
                .toList();
        List<ExamQuestion> sourceQuestions = examQuestionRepository.findAllById(sourceQuestionIds);
        if (sourceQuestions == null || sourceQuestions.isEmpty()) return null;
        Map<Long, ExamQuestion> sourceById = sourceQuestions.stream()
                .filter(source -> source.getId() != null)
                .collect(Collectors.toMap(
                        ExamQuestion::getId,
                        Function.identity(),
                        (left, right) -> left));

        LinkedHashMap<String, AssessmentAggregate> assessments = new LinkedHashMap<>();
        for (ExamPaperQuestion paperQuestion : eligibleQuestions) {
            ExamQuestion source = sourceById.get(paperQuestion.getQuestionId());
            if (source == null) continue;
            ExamPaperAttemptAnswer answer = answers.get(paperQuestion.getId());
            for (String knowledgePoint : readPythonKnowledgePoints(source.getKnowledgePointsJson())) {
                assessments.compute(knowledgePoint, (key, existing) -> {
                    if (existing == null) {
                        return new AssessmentAggregate(
                                Boolean.TRUE.equals(answer.getCorrect()), source.getDifficulty());
                    }
                    existing.merge(Boolean.TRUE.equals(answer.getCorrect()), source.getDifficulty());
                    return existing;
                });
            }
        }
        if (assessments.isEmpty()) return null;

        LearningPathDTO.HomeView homeBefore = learningPathService.getHomeForFeedback(
                attempt.getUserId(), PYTHON);
        LearningPathDTO.PathView pathBefore = homeBefore == null ? null : homeBefore.getActivePath();
        Map<String, LearningPathDTO.MasteryView> masteryBefore = homeBefore == null
                || homeBefore.getMastery() == null
                ? Map.of()
                : homeBefore.getMastery().stream()
                .filter(item -> item != null && item.getKnowledgePointKey() != null)
                .collect(Collectors.toMap(
                        LearningPathDTO.MasteryView::getKnowledgePointKey,
                        Function.identity(),
                        (left, right) -> left));

        List<AppExamDTO.MasteryChange> masteryChanges = new ArrayList<>();
        List<String> assessedKnowledgePoints = new ArrayList<>();
        List<String> weakKnowledgePoints = new ArrayList<>();
        int correctKnowledgePoints = 0;
        for (Map.Entry<String, AssessmentAggregate> entry : assessments.entrySet()) {
            String knowledgePoint = entry.getKey();
            AssessmentAggregate aggregate = entry.getValue();
            LearningPathDTO.AssessmentObservation observation = new LearningPathDTO.AssessmentObservation();
            observation.setUserId(attempt.getUserId());
            observation.setAttemptId(attempt.getId());
            observation.setCourseKey(PYTHON);
            observation.setKnowledgePointKey(knowledgePoint);
            observation.setKnowledgePointName(knowledgePoint);
            observation.setCorrect(aggregate.correct());
            observation.setDifficulty(aggregate.difficulty());

            LearningPathDTO.MasteryView after = learningPathService.applyAssessment(observation);
            if (after == null) {
                throw new IllegalStateException("Learning path service returned no mastery result");
            }
            LearningPathDTO.MasteryView before = masteryBefore.get(knowledgePoint);
            AppExamDTO.MasteryChange change = new AppExamDTO.MasteryChange();
            change.setKnowledgePointKey(knowledgePoint);
            change.setCorrect(aggregate.correct());
            change.setScoreBefore(before == null || before.getScore() == null
                    ? BigDecimal.ZERO : before.getScore());
            change.setScoreAfter(after.getScore());
            change.setStatusBefore(before == null ? "new" : before.getStatus());
            change.setStatusAfter(after.getStatus());
            masteryChanges.add(change);
            assessedKnowledgePoints.add(knowledgePoint);
            if (aggregate.correct()) {
                correctKnowledgePoints++;
            } else {
                weakKnowledgePoints.add(knowledgePoint);
            }
        }

        List<AppExamDTO.ProfileEvidenceUpdate> profileEvidence = recordProfileEvidence(
                attempt,
                assessedKnowledgePoints,
                weakKnowledgePoints,
                correctKnowledgePoints,
                now);
        ReplanResult replan = replanForWeakKnowledgePoints(
                attempt, pathBefore, weakKnowledgePoints, now);

        AppExamDTO.LearningUpdate update = new AppExamDTO.LearningUpdate();
        update.setSchemaVersion("exam-learning-update-v1");
        update.setAssessedKnowledgePoints(List.copyOf(assessedKnowledgePoints));
        update.setWeakKnowledgePoints(List.copyOf(weakKnowledgePoints));
        update.setMasteryChanges(List.copyOf(masteryChanges));
        update.setProfileEvidence(List.copyOf(profileEvidence));
        update.setEvidenceStatus(profileEvidence.stream()
                .map(AppExamDTO.ProfileEvidenceUpdate::getStatus)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(",")));
        update.setPathVersionBefore(pathBefore == null ? null : pathBefore.getVersion());
        update.setPathVersionAfter(replan.pathAfter() == null
                ? (pathBefore == null ? null : pathBefore.getVersion())
                : replan.pathAfter().getVersion());
        update.setReplanned(replan.replanned());
        update.setReplanReason(replan.reason());
        update.setChangedNodes(replan.changedNodes());
        update.setNextRecommendation(nextRecommendation(
                replan.pathAfter() == null ? pathBefore : replan.pathAfter()));
        return update;
    }

    private List<String> readPythonKnowledgePoints(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (JsonNode item : root) {
                if (!item.isTextual()) continue;
                String key = item.asText().trim();
                if (key.length() <= 160
                        && PYTHON_KNOWLEDGE_POINT.matcher(key).matches()
                        && seen.add(key)) {
                    result.add(key);
                }
            }
            return result;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private List<AppExamDTO.ProfileEvidenceUpdate> recordProfileEvidence(
            ExamPaperAttempt attempt,
            List<String> assessed,
            List<String> weak,
            int correctCount,
            LocalDateTime now) {
        List<AppExamDTO.ProfileEvidenceUpdate> updates = new ArrayList<>();
        if (!weak.isEmpty()) {
            updates.add(addProfileEvidence(
                    attempt,
                    "weak_points",
                    "wrong_question",
                    "weakness",
                    -2,
                    "本次 Python 测试暴露薄弱知识点：" + String.join("、", weak),
                    String.join("、", weak),
                    assessed,
                    correctCount,
                    now));
        }
        updates.add(addProfileEvidence(
                attempt,
                "learning_progress",
                "exam",
                "increase",
                1,
                "用户完成了一次 Python 客观题测试，共形成 " + assessed.size() + " 个知识点掌握度观察。",
                "Python 测试提交",
                assessed,
                correctCount,
                now));
        int abilityDelta = correctCount * 2 >= assessed.size() ? 1 : -1;
        updates.add(addProfileEvidence(
                attempt,
                "ability_performance",
                "question_result",
                abilityDelta > 0 ? "increase" : "decrease",
                abilityDelta,
                "本次 Python 客观题知识点正确 " + correctCount + "/" + assessed.size()
                        + "，结果仅作为候选能力证据。",
                "Python 客观题表现",
                assessed,
                correctCount,
                now));
        return updates;
    }

    private AppExamDTO.ProfileEvidenceUpdate addProfileEvidence(
            ExamPaperAttempt attempt,
            String dimensionKey,
            String sourceType,
            String direction,
            int suggestedDelta,
            String evidenceText,
            String objectName,
            List<String> assessed,
            int correctCount,
            LocalDateTime now) {
        String sourceId = "exam-attempt-" + attempt.getId() + ":" + dimensionKey;
        UserProfileDTO.EvidenceRequest request = new UserProfileDTO.EvidenceRequest();
        request.setDimensionKey(dimensionKey);
        request.setSourceType(sourceType);
        request.setSourceId(sourceId);
        request.setAction("answered");
        request.setObjectType("exam_attempt");
        request.setObjectId(String.valueOf(attempt.getId()));
        request.setObjectName(limit(objectName, 200));
        request.setResult(limit(correctCount + "/" + assessed.size() + " 个知识点正确", 300));
        request.setOccurredAt(now);
        request.setEvidenceTags(assessed.stream().limit(20).toList());
        request.setEvidence(limit(evidenceText, 1000));
        request.setDirection(direction);
        request.setConfidence(0.85);
        request.setSuggestedDelta(suggestedDelta);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("submitter", "AppExamServiceImpl");
        metadata.put("attemptId", attempt.getId());
        metadata.put("paperId", attempt.getPaperId());
        metadata.put("courseKey", PYTHON);
        metadata.put("assessedKnowledgePointCount", assessed.size());
        metadata.put("correctKnowledgePointCount", correctCount);
        request.setMetadata(metadata);

        UserProfileDTO.EvidenceResponse response = userProfileService.addEvidence(
                attempt.getUserId(), request);
        AppExamDTO.ProfileEvidenceUpdate update = new AppExamDTO.ProfileEvidenceUpdate();
        update.setDimensionKey(dimensionKey);
        update.setSourceId(sourceId);
        update.setStatus(response == null ? "recorded" : response.getStatus());
        update.setAccepted(response == null ? null : response.getAccepted());
        return update;
    }

    private ReplanResult replanForWeakKnowledgePoints(
            ExamPaperAttempt attempt,
            LearningPathDTO.PathView pathBefore,
            List<String> weakKnowledgePoints,
            LocalDateTime now) {
        if (weakKnowledgePoints.isEmpty()) {
            return new ReplanResult(false, "本次未发现新的 Python 薄弱知识点", pathBefore, List.of());
        }
        if (pathBefore == null || pathBefore.getItems() == null || pathBefore.getItems().isEmpty()) {
            return new ReplanResult(
                    false,
                    "已识别薄弱点，但当前没有可调整的活动 Python 学习路径",
                    pathBefore,
                    List.of());
        }

        Set<String> weak = new HashSet<>(weakKnowledgePoints);
        List<LearningPathDTO.PathItemView> ordered = pathBefore.getItems().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if (ordered.isEmpty()) {
            return new ReplanResult(
                    false,
                    "已识别薄弱点，但当前活动 Python 学习路径没有可调整节点",
                    pathBefore,
                    List.of());
        }
        ordered.sort((left, right) -> {
            int weakOrder = Boolean.compare(
                    !weak.contains(left.getKnowledgePoint()),
                    !weak.contains(right.getKnowledgePoint()));
            if (weakOrder != 0) return weakOrder;
            int leftSequence = left.getSequenceNo() == null ? Integer.MAX_VALUE : left.getSequenceNo();
            int rightSequence = right.getSequenceNo() == null ? Integer.MAX_VALUE : right.getSequenceNo();
            int sequenceOrder = Integer.compare(leftSequence, rightSequence);
            if (sequenceOrder != 0) return sequenceOrder;
            return clean(left.getItemKey()).compareTo(clean(right.getItemKey()));
        });

        List<LearningPathDTO.PathItemDraft> itemDrafts = new ArrayList<>();
        List<AppExamDTO.PathChange> changes = new ArrayList<>();
        Set<String> existingKnowledgePoints = ordered.stream()
                .map(LearningPathDTO.PathItemView::getKnowledgePoint)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> usedItemKeys = ordered.stream()
                .map(LearningPathDTO.PathItemView::getItemKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int nextSequence = 1;
        for (String missingKnowledgePoint : weakKnowledgePoints.stream()
                .filter(point -> !existingKnowledgePoints.contains(point))
                .toList()) {
            LearningPathDTO.PathItemDraft added = new LearningPathDTO.PathItemDraft();
            added.setItemKey(uniqueReviewItemKey(missingKnowledgePoint, usedItemKeys));
            added.setKnowledgePoint(missingKnowledgePoint);
            added.setObjective("巩固考试薄弱知识点 " + missingKnowledgePoint);
            added.setTargetMastery(new BigDecimal("80.00"));
            added.setPriority(1);
            added.setSequenceNo(nextSequence);
            added.setResourceKinds(List.of("knowledge_note", "practice_set"));
            added.setResourceIds(List.of());
            added.setStatus("needs_review");
            added.setDeliveryStatus("pending");
            added.setSourceMessageId(pathBefore.getSourceMessageId());
            added.setScheduledAt(now);
            added.setRationale("考试反馈：路径中缺少该薄弱知识点，已新增优先复习节点");
            itemDrafts.add(added);

            AppExamDTO.PathChange change = new AppExamDTO.PathChange();
            change.setItemKey(added.getItemKey());
            change.setKnowledgePoint(missingKnowledgePoint);
            change.setSequenceBefore(null);
            change.setSequenceAfter(nextSequence);
            change.setStatusBefore(null);
            change.setStatusAfter("needs_review");
            change.setReason("客观题答错且原路径缺少该知识点，新增优先复习节点");
            changes.add(change);
            nextSequence++;
        }

        for (LearningPathDTO.PathItemView source : ordered) {
            int sourceNextSequence = nextSequence++;
            boolean needsReview = weak.contains(source.getKnowledgePoint());
            String nextStatus = needsReview ? "needs_review" : source.getStatus();
            String nextRationale = needsReview
                    ? appendRationale(source.getRationale(), "考试反馈：该知识点需要优先巩固")
                    : source.getRationale();

            LearningPathDTO.PathItemDraft draft = new LearningPathDTO.PathItemDraft();
            draft.setItemKey(source.getItemKey());
            draft.setKnowledgePoint(source.getKnowledgePoint());
            draft.setObjective(source.getObjective());
            draft.setTargetMastery(source.getTargetMastery());
            draft.setPriority(source.getPriority());
            draft.setSequenceNo(sourceNextSequence);
            draft.setResourceKinds(source.getResourceKinds());
            draft.setResourceIds(source.getResourceIds());
            draft.setStatus(nextStatus);
            draft.setDeliveryStatus(source.getDeliveryStatus());
            draft.setSourceMessageId(source.getSourceMessageId());
            draft.setScheduledAt(source.getScheduledAt());
            draft.setRationale(nextRationale);
            itemDrafts.add(draft);

            boolean changed = !Objects.equals(source.getSequenceNo(), sourceNextSequence)
                    || !Objects.equals(source.getStatus(), nextStatus)
                    || !Objects.equals(source.getRationale(), nextRationale);
            if (changed) {
                AppExamDTO.PathChange change = new AppExamDTO.PathChange();
                change.setItemKey(source.getItemKey());
                change.setKnowledgePoint(source.getKnowledgePoint());
                change.setSequenceBefore(source.getSequenceNo());
                change.setSequenceAfter(sourceNextSequence);
                change.setStatusBefore(source.getStatus());
                change.setStatusAfter(nextStatus);
                change.setReason(needsReview ? "客观题答错，提升为优先复习节点" : "随薄弱节点重新排序");
                changes.add(change);
            }
        }

        if (changes.isEmpty()) {
            return new ReplanResult(
                    false,
                    "薄弱节点已处于优先复习状态，学习路径未发生变化",
                    pathBefore,
                    List.of());
        }

        LearningPathDTO.PathDraft draft = new LearningPathDTO.PathDraft();
        draft.setCourseKey(PYTHON);
        draft.setGoal(pathBefore.getGoal());
        draft.setProfileDigest(pathBefore.getProfileDigest());
        draft.setMasteryDigest(boundedDigest(
                "考试 " + attempt.getId() + " 反馈薄弱点：" + String.join("、", weakKnowledgePoints),
                128));
        draft.setSourceMessageId(pathBefore.getSourceMessageId());
        draft.setGeneratedAt(now);
        draft.setNextReplanAt(pathBefore.getNextReplanAt());
        draft.setItems(itemDrafts);
        LearningPathDTO.PathView pathAfter = learningPathService.replaceActivePath(
                attempt.getUserId(), draft);
        return new ReplanResult(
                true,
                "根据考试薄弱点重规划：" + String.join("、", weakKnowledgePoints),
                pathAfter,
                List.copyOf(changes));
    }

    private LearningPathDTO.Recommendation nextRecommendation(LearningPathDTO.PathView path) {
        if (path == null || path.getItems() == null) return null;
        return path.getItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> !"completed".equals(item.getStatus()))
                .sorted((left, right) -> Integer.compare(
                        left.getSequenceNo() == null ? Integer.MAX_VALUE : left.getSequenceNo(),
                        right.getSequenceNo() == null ? Integer.MAX_VALUE : right.getSequenceNo()))
                .findFirst()
                .map(item -> {
                    LearningPathDTO.Recommendation recommendation = new LearningPathDTO.Recommendation();
                    recommendation.setItemId(item.getId());
                    recommendation.setItemKey(item.getItemKey());
                    recommendation.setKnowledgePoint(item.getKnowledgePoint());
                    recommendation.setObjective(item.getObjective());
                    recommendation.setPriority(item.getPriority());
                    recommendation.setResourceIds(item.getResourceIds());
                    recommendation.setStatus(item.getStatus());
                    recommendation.setRationale(item.getRationale());
                    return recommendation;
                })
                .orElse(null);
    }

    private String appendRationale(String existing, String feedback) {
        if (existing == null || existing.isBlank()) return feedback;
        return existing.contains(feedback) ? existing : existing + "；" + feedback;
    }

    private String uniqueReviewItemKey(String knowledgePoint, Set<String> usedItemKeys) {
        String base = "exam-review-" + sha256(knowledgePoint);
        String candidate = base;
        int suffix = 2;
        while (!usedItemKeys.add(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String boundedDigest(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        String suffix = "…#" + sha256(value).substring(0, 12);
        return value.substring(0, maxLength - suffix.length()) + suffix;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private String limit(String value, int maxLength) {
        String text = value == null ? "" : value;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String writeLearningUpdate(AppExamDTO.LearningUpdate update) {
        try {
            return objectMapper.writeValueAsString(update);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize exam learning update", error);
        }
    }

    private AppExamDTO.LearningUpdate readLearningUpdate(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, AppExamDTO.LearningUpdate.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored exam learning update is malformed", error);
        }
    }

    private static final class AssessmentAggregate {
        private boolean correct;
        private String difficulty;

        private AssessmentAggregate(boolean correct, String difficulty) {
            this.correct = correct;
            this.difficulty = normalizeDifficultyValue(difficulty);
        }

        private void merge(boolean anotherCorrect, String anotherDifficulty) {
            correct = correct && anotherCorrect;
            if (difficultyWeight(anotherDifficulty) > difficultyWeight(difficulty)) {
                difficulty = normalizeDifficultyValue(anotherDifficulty);
            }
        }

        private boolean correct() {
            return correct;
        }

        private String difficulty() {
            return difficulty;
        }

        private static int difficultyWeight(String difficulty) {
            return switch (normalizeDifficultyValue(difficulty)) {
                case "hard" -> 3;
                case "medium" -> 2;
                default -> 1;
            };
        }

        private static String normalizeDifficultyValue(String difficulty) {
            if (difficulty == null) return "easy";
            return switch (difficulty.trim().toLowerCase()) {
                case "hard" -> "hard";
                case "medium" -> "medium";
                default -> "easy";
            };
        }
    }

    private record ReplanResult(
            boolean replanned,
            String reason,
            LearningPathDTO.PathView pathAfter,
            List<AppExamDTO.PathChange> changedNodes) {
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
