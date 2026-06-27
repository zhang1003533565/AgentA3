package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.ExamQuestionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ExamQuestionServiceImpl implements ExamQuestionService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "single_choice",
            "multiple_choice",
            "true_false",
            "fill_blank",
            "short_answer",
            "essay",
            "material_analysis",
            "calculation",
            "proof",
            "programming",
            "operation",
            "matching",
            "ordering",
            "cloze"
    );

    private static final Set<String> ALLOWED_DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> ALLOWED_SCORING_MODES = Set.of("exact", "blank", "rubric", "step", "program", "manual");
    private static final Set<String> REQUIRED_QUESTION_KEYS = Set.of(
            "id",
            "type",
            "stem",
            "score",
            "difficulty",
            "knowledgePoints",
            "tags",
            "body",
            "answer",
            "analysis",
            "scoring",
            "sourceBasis"
    );

    private final ExamQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    public ExamQuestionServiceImpl(ExamQuestionRepository questionRepository, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExamQuestionDTO.ReviewResponse review(ExamQuestionDTO.ImportRequest request, String expectedType) {
        ExamQuestionDTO.ReviewResponse response = new ExamQuestionDTO.ReviewResponse();
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> types = new TreeSet<>();

        if (request == null) {
            issues.add("请求体不能为空");
            return buildReviewResponse(response, issues, warnings, 0, types);
        }
        if (expectedType != null && !expectedType.isBlank() && !ALLOWED_TYPES.contains(expectedType)) {
            issues.add("expectedType 不是合法题型枚举");
        }

        List<Map<String, Object>> questions = request.getQuestions();
        if (questions == null) {
            issues.add("questions 必须是数组");
            questions = List.of();
        }
        if (request.getMissingInfo() == null) {
            issues.add("missingInfo 必须是数组");
        }
        if (questions.isEmpty() && (request.getMissingInfo() == null || request.getMissingInfo().isEmpty())) {
            issues.add("未生成题目时 missingInfo 必须说明缺失信息");
        }

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            reviewQuestion(question, "questions[" + (i + 1) + "]", expectedType, issues, warnings);
            Object type = question == null ? null : question.get("type");
            if (type instanceof String) {
                types.add((String) type);
            }
        }

        return buildReviewResponse(response, issues, warnings, questions.size(), types);
    }

    @Override
    public ExamQuestionDTO.ImportResponse importQuestions(ExamQuestionDTO.ImportRequest request, String expectedType, Long userId) {
        ExamQuestionDTO.ReviewResponse review = review(request, expectedType);
        if (!Boolean.TRUE.equals(review.getValid())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题库 JSON 未通过校验：" + String.join("；", review.getIssues()));
        }
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "没有可导入的题目");
        }

        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> question : request.getQuestions()) {
            ExamQuestion entity = new ExamQuestion();
            entity.setSourceQuestionId(asString(question.get("id")));
            entity.setType(asString(question.get("type")));
            entity.setStem(asString(question.get("stem")));
            entity.setScore(toBigDecimal(question.get("score")));
            entity.setDifficulty(asString(question.get("difficulty")));
            entity.setKnowledgePointsJson(toJson(question.get("knowledgePoints")));
            entity.setTagsJson(toJson(question.get("tags")));
            entity.setBodyJson(toJson(question.get("body")));
            entity.setAnswerJson(toJson(question.get("answer")));
            entity.setAnalysis(asString(question.get("analysis")));
            entity.setScoringJson(toJson(question.get("scoring")));
            entity.setSourceBasisJson(toJson(question.get("sourceBasis")));
            entity.setRawQuestionJson(toJson(question));
            entity.setSourceAgent(blankToNull(request.getSourceAgent()));
            entity.setSourceTitle(blankToNull(request.getSourceTitle()));
            entity.setCreatedBy(userId);
            ids.add(questionRepository.save(entity).getId());
        }

        ExamQuestionDTO.ImportResponse response = new ExamQuestionDTO.ImportResponse();
        response.setValid(review.getValid());
        response.setIssues(review.getIssues());
        response.setWarnings(review.getWarnings());
        response.setQuestionCount(review.getQuestionCount());
        response.setTypes(review.getTypes());
        response.setImportedCount(ids.size());
        response.setQuestionIds(ids);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExamQuestionDTO.QuestionVO> listQuestions(Integer current, Integer size, String type, String difficulty, String keyword) {
        int page = current == null || current < 1 ? 1 : current;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        String normalizedType = blankToNull(type);
        String normalizedDifficulty = blankToNull(difficulty);
        String normalizedKeyword = blankToNull(keyword);
        if (normalizedType != null && !ALLOWED_TYPES.contains(normalizedType)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题型不合法");
        }
        if (normalizedDifficulty != null && !ALLOWED_DIFFICULTIES.contains(normalizedDifficulty)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "难度不合法");
        }
        Page<ExamQuestion> result = questionRepository.search(
                normalizedType,
                normalizedDifficulty,
                normalizedKeyword,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::toVO).collect(Collectors.toList()),
                result.getTotalElements(),
                page,
                pageSize
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ExamQuestionDTO.QuestionVO getQuestion(Long id) {
        ExamQuestion question = questionRepository.findById(id)
                .filter(item -> item.getStatus() != null && item.getStatus() == 1)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题目不存在"));
        return toVO(question);
    }

    private ExamQuestionDTO.ReviewResponse buildReviewResponse(
            ExamQuestionDTO.ReviewResponse response,
            List<String> issues,
            List<String> warnings,
            Integer questionCount,
            Set<String> types) {
        response.setValid(issues.isEmpty());
        response.setIssues(issues);
        response.setWarnings(warnings);
        response.setQuestionCount(questionCount);
        response.setTypes(new ArrayList<>(types));
        return response;
    }

    @SuppressWarnings("unchecked")
    private void reviewQuestion(
            Map<String, Object> question,
            String path,
            String expectedType,
            List<String> issues,
            List<String> warnings) {
        if (question == null) {
            issues.add(path + " 必须是对象");
            return;
        }
        Set<String> missingKeys = new TreeSet<>(REQUIRED_QUESTION_KEYS);
        missingKeys.removeAll(question.keySet());
        if (!missingKeys.isEmpty()) {
            issues.add(path + " 缺少字段：" + String.join(", ", missingKeys));
            return;
        }

        String type = asString(question.get("type"));
        if (!ALLOWED_TYPES.contains(type)) {
            issues.add(path + ".type 必须是合法题型枚举");
            return;
        }
        if (expectedType != null && !expectedType.isBlank() && !expectedType.equals(type)) {
            issues.add(path + ".type 必须是 " + expectedType + "，实际为 " + type);
        }
        requireString(question, "id", path, issues, false);
        requireString(question, "stem", path, issues, false);
        requireString(question, "difficulty", path, issues, false);
        if (!ALLOWED_DIFFICULTIES.contains(asString(question.get("difficulty")))) {
            issues.add(path + ".difficulty 必须是 easy、medium 或 hard");
        }
        requirePositiveNumber(question, "score", path, issues);
        requireList(question, "knowledgePoints", path, issues);
        requireList(question, "tags", path, issues);
        requireList(question, "sourceBasis", path, issues);
        requireString(question, "analysis", path, issues, true);

        Map<String, Object> body = requireObject(question, "body", path, issues);
        Map<String, Object> answer = requireObject(question, "answer", path, issues);
        Map<String, Object> scoring = requireObject(question, "scoring", path, issues);
        if (body == null || answer == null || scoring == null) {
            return;
        }
        if (question.get("knowledgePoints") instanceof List<?> knowledgePoints && knowledgePoints.isEmpty()) {
            warnings.add(path + ".knowledgePoints 为空，建议保留知识点");
        }
        if (question.get("sourceBasis") instanceof List<?> sourceBasis && sourceBasis.isEmpty()) {
            warnings.add(path + ".sourceBasis 为空，AI 生成题建议保留生成依据");
        }

        reviewScoring(question, path, issues);
        switch (type) {
            case "single_choice" -> validateSingleChoice(body, answer, path, issues);
            case "multiple_choice" -> validateMultipleChoice(body, answer, path, issues);
            case "true_false" -> validateTrueFalse(body, answer, path, issues);
            case "fill_blank" -> validateFillBlank(body, answer, path, issues);
            case "short_answer" -> validateShortAnswer(answer, path, issues);
            case "essay" -> validateEssay(answer, path, issues);
            case "material_analysis" -> validateMaterialAnalysis(body, path, issues, warnings);
            case "calculation" -> validateCalculation(answer, path, issues);
            case "proof" -> validateProof(answer, path, issues);
            case "programming" -> validateProgramming(body, answer, path, issues);
            case "operation" -> validateOperation(body, answer, path, issues);
            case "matching" -> validateMatching(body, answer, path, issues);
            case "ordering" -> validateOrdering(body, answer, path, issues);
            case "cloze" -> validateCloze(body, answer, path, issues);
            default -> issues.add(path + ".type 暂不支持");
        }
    }

    private void reviewScoring(Map<String, Object> question, String path, List<String> issues) {
        BigDecimal score = toBigDecimal(question.get("score"));
        Map<String, Object> scoring = castMap(question.get("scoring"));
        String mode = asString(scoring.get("mode"));
        if (!ALLOWED_SCORING_MODES.contains(mode)) {
            issues.add(path + ".scoring.mode 必须是 exact、blank、rubric、step、program 或 manual");
            return;
        }
        Object rubricsObj = scoring.get("rubrics");
        if (!(rubricsObj instanceof List<?> rubrics)) {
            issues.add(path + ".scoring.rubrics 必须是数组");
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasScores = false;
        for (int i = 0; i < rubrics.size(); i++) {
            Object item = rubrics.get(i);
            if (!(item instanceof Map<?, ?> rubric)) {
                issues.add(path + ".scoring.rubrics[" + (i + 1) + "] 必须是对象");
                continue;
            }
            Object criterion = rubric.get("criterion");
            if (!(criterion instanceof String) || ((String) criterion).isBlank()) {
                issues.add(path + ".scoring.rubrics[" + (i + 1) + "].criterion 必须是非空字符串");
            }
            BigDecimal rubricScore = toBigDecimal(rubric.get("score"));
            if (rubricScore == null) {
                issues.add(path + ".scoring.rubrics[" + (i + 1) + "].score 必须是数字");
            } else {
                hasScores = true;
                total = total.add(rubricScore);
            }
        }
        if (Set.of("rubric", "step", "program", "manual").contains(mode)) {
            if (rubrics.isEmpty()) {
                issues.add(path + ".scoring.rubrics 在 " + mode + " 模式下不能为空");
            } else if (hasScores && score != null && total.compareTo(score) != 0) {
                issues.add(path + ".score 必须等于 scoring.rubrics 分值之和");
            }
        }
    }

    private void validateSingleChoice(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        Set<String> optionKeys = optionKeys(body, path + ".body.options", issues);
        String correct = asString(answer.get("correctOption"));
        if (correct == null || correct.isBlank()) {
            issues.add(path + ".answer.correctOption 必须是非空字符串");
        } else if (!optionKeys.isEmpty() && !optionKeys.contains(correct)) {
            issues.add(path + ".answer.correctOption 必须出现在 body.options.key 中");
        }
    }

    private void validateMultipleChoice(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        Set<String> optionKeys = optionKeys(body, path + ".body.options", issues);
        Object correctObj = answer.get("correctOptions");
        if (!(correctObj instanceof List<?> correctOptions) || correctOptions.isEmpty()) {
            issues.add(path + ".answer.correctOptions 必须是非空数组");
            return;
        }
        for (Object item : correctOptions) {
            if (!(item instanceof String option)) {
                issues.add(path + ".answer.correctOptions 中每个值都必须是字符串");
            } else if (!optionKeys.isEmpty() && !optionKeys.contains(option)) {
                issues.add(path + ".answer.correctOptions 的 " + option + " 未出现在 body.options.key 中");
            }
        }
    }

    private void validateTrueFalse(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        requireString(body, "statement", path + ".body", issues, false);
        if (!(answer.get("correct") instanceof Boolean)) {
            issues.add(path + ".answer.correct 必须是布尔值");
        }
    }

    private void validateFillBlank(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        requireString(body, "text", path + ".body", issues, false);
        Set<String> blankIds = blankIds(body, path + ".body.blanks", issues);
        Object answerBlanksObj = answer.get("blanks");
        if (!(answerBlanksObj instanceof List<?> answerBlanks) || answerBlanks.isEmpty()) {
            issues.add(path + ".answer.blanks 必须是非空数组");
            return;
        }
        for (int i = 0; i < answerBlanks.size(); i++) {
            if (!(answerBlanks.get(i) instanceof Map<?, ?> blankAnswer)) {
                issues.add(path + ".answer.blanks[" + (i + 1) + "] 必须是对象");
                continue;
            }
            Object id = blankAnswer.get("id");
            if (!(id instanceof String) || !blankIds.contains(id)) {
                issues.add(path + ".answer.blanks[" + (i + 1) + "].id 必须对应 body.blanks.id");
            }
            Object answers = blankAnswer.get("answers");
            if (!(answers instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
                issues.add(path + ".answer.blanks[" + (i + 1) + "].answers 必须是字符串数组");
            }
        }
    }

    private void validateShortAnswer(Map<String, Object> answer, String path, List<String> issues) {
        requireString(answer, "referenceAnswer", path + ".answer", issues, false);
        requireStringList(answer, "answerPoints", path + ".answer", issues);
    }

    private void validateEssay(Map<String, Object> answer, String path, List<String> issues) {
        requireString(answer, "referenceAnswer", path + ".answer", issues, false);
        requireStringList(answer, "keyPoints", path + ".answer", issues);
    }

    private void validateMaterialAnalysis(Map<String, Object> body, String path, List<String> issues, List<String> warnings) {
        requireString(body, "material", path + ".body", issues, false);
        Object subQuestionsObj = body.get("subQuestions");
        if (!(subQuestionsObj instanceof List<?> subQuestions) || subQuestions.isEmpty()) {
            issues.add(path + ".body.subQuestions 必须是非空数组");
            return;
        }
        for (int i = 0; i < subQuestions.size(); i++) {
            Object item = subQuestions.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                issues.add(path + ".body.subQuestions[" + (i + 1) + "] 必须是对象");
                continue;
            }
            reviewQuestion(toStringObjectMap(map), path + ".body.subQuestions[" + (i + 1) + "]", null, issues, warnings);
        }
    }

    private void validateCalculation(Map<String, Object> answer, String path, List<String> issues) {
        requireString(answer, "finalAnswer", path + ".answer", issues, false);
        requireStringList(answer, "steps", path + ".answer", issues);
    }

    private void validateProof(Map<String, Object> answer, String path, List<String> issues) {
        requireStringList(answer, "proofSteps", path + ".answer", issues);
        requireString(answer, "conclusion", path + ".answer", issues, false);
    }

    private void validateProgramming(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        for (String key : List.of("title", "description", "language", "inputFormat", "outputFormat")) {
            requireString(body, key, path + ".body", issues, false);
        }
        requireList(body, "constraints", path + ".body", issues);
        requireList(body, "examples", path + ".body", issues);
        requireStringList(answer, "solutionOutline", path + ".answer", issues);
        requireString(answer, "referenceSolution", path + ".answer", issues, true);
        Object testCasesObj = answer.get("testCases");
        if (!(testCasesObj instanceof List<?> testCases)) {
            issues.add(path + ".answer.testCases 必须是数组");
            return;
        }
        for (int i = 0; i < testCases.size(); i++) {
            if (!(testCases.get(i) instanceof Map<?, ?> testCase)) {
                issues.add(path + ".answer.testCases[" + (i + 1) + "] 必须是对象");
                continue;
            }
            Map<String, Object> caseMap = toStringObjectMap(testCase);
            requireString(caseMap, "input", path + ".answer.testCases[" + (i + 1) + "]", issues, true);
            requireString(caseMap, "expectedOutput", path + ".answer.testCases[" + (i + 1) + "]", issues, true);
            if (!(caseMap.get("hidden") instanceof Boolean)) {
                issues.add(path + ".answer.testCases[" + (i + 1) + "].hidden 必须是布尔值");
            }
        }
    }

    private void validateOperation(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        requireString(body, "task", path + ".body", issues, false);
        requireList(body, "requirements", path + ".body", issues);
        requireString(answer, "expectedResult", path + ".answer", issues, false);
    }

    private void validateMatching(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        Set<String> leftKeys = itemKeys(body.get("leftItems"), path + ".body.leftItems", issues);
        Set<String> rightKeys = itemKeys(body.get("rightItems"), path + ".body.rightItems", issues);
        Object pairsObj = answer.get("pairs");
        if (!(pairsObj instanceof List<?> pairs) || pairs.isEmpty()) {
            issues.add(path + ".answer.pairs 必须是非空数组");
            return;
        }
        for (int i = 0; i < pairs.size(); i++) {
            if (!(pairs.get(i) instanceof Map<?, ?> pair)) {
                issues.add(path + ".answer.pairs[" + (i + 1) + "] 必须是对象");
                continue;
            }
            if (!leftKeys.contains(pair.get("left"))) {
                issues.add(path + ".answer.pairs[" + (i + 1) + "].left 必须出现在 body.leftItems.key 中");
            }
            if (!rightKeys.contains(pair.get("right"))) {
                issues.add(path + ".answer.pairs[" + (i + 1) + "].right 必须出现在 body.rightItems.key 中");
            }
        }
    }

    private void validateOrdering(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        Set<String> keys = itemKeys(body.get("items"), path + ".body.items", issues);
        Object orderedKeysObj = answer.get("orderedKeys");
        if (!(orderedKeysObj instanceof List<?> orderedKeys) || orderedKeys.stream().anyMatch(item -> !(item instanceof String))) {
            issues.add(path + ".answer.orderedKeys 必须是字符串数组");
            return;
        }
        if (!new HashSet<>(orderedKeys).equals(keys)) {
            issues.add(path + ".answer.orderedKeys 必须与 body.items.key 完全一致");
        }
    }

    private void validateCloze(Map<String, Object> body, Map<String, Object> answer, String path, List<String> issues) {
        requireString(body, "text", path + ".body", issues, false);
        optionKeys(body, path + ".body.options", issues);
        Set<String> blankIds = blankIds(body, path + ".body.blanks", issues);
        Object blanksObj = answer.get("blanks");
        if (!(blanksObj instanceof List<?> blanks) || blanks.isEmpty()) {
            issues.add(path + ".answer.blanks 必须是非空数组");
            return;
        }
        for (int i = 0; i < blanks.size(); i++) {
            if (!(blanks.get(i) instanceof Map<?, ?> item)) {
                issues.add(path + ".answer.blanks[" + (i + 1) + "] 必须是对象");
                continue;
            }
            if (!blankIds.contains(item.get("id"))) {
                issues.add(path + ".answer.blanks[" + (i + 1) + "].id 必须对应 body.blanks.id");
            }
            if (!(item.get("correctOption") instanceof String)) {
                issues.add(path + ".answer.blanks[" + (i + 1) + "].correctOption 必须是字符串");
            }
        }
    }

    private Set<String> optionKeys(Map<String, Object> body, String path, List<String> issues) {
        Object optionsObj = body.get("options");
        if (!(optionsObj instanceof List<?> options) || options.isEmpty()) {
            issues.add(path + " 必须是非空数组");
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < options.size(); i++) {
            if (!(options.get(i) instanceof Map<?, ?> option)) {
                issues.add(path + "[" + (i + 1) + "] 必须是对象");
                continue;
            }
            Object key = option.get("key");
            Object text = option.get("text");
            if (!(key instanceof String) || ((String) key).isBlank()) {
                issues.add(path + "[" + (i + 1) + "].key 必须是非空字符串");
            } else {
                keys.add((String) key);
            }
            if (!(text instanceof String) || ((String) text).isBlank()) {
                issues.add(path + "[" + (i + 1) + "].text 必须是非空字符串");
            }
        }
        return keys;
    }

    private Set<String> blankIds(Map<String, Object> body, String path, List<String> issues) {
        Object blanksObj = body.get("blanks");
        if (!(blanksObj instanceof List<?> blanks) || blanks.isEmpty()) {
            issues.add(path + " 必须是非空数组");
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < blanks.size(); i++) {
            if (!(blanks.get(i) instanceof Map<?, ?> blank)) {
                issues.add(path + "[" + (i + 1) + "] 必须是对象");
                continue;
            }
            Object id = blank.get("id");
            if (!(id instanceof String) || ((String) id).isBlank()) {
                issues.add(path + "[" + (i + 1) + "].id 必须是非空字符串");
            } else {
                ids.add((String) id);
            }
            if (toBigDecimal(blank.get("score")) == null) {
                issues.add(path + "[" + (i + 1) + "].score 必须是数字");
            }
        }
        return ids;
    }

    private Set<String> itemKeys(Object itemsObj, String path, List<String> issues) {
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            issues.add(path + " 必须是非空数组");
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> item)) {
                issues.add(path + "[" + (i + 1) + "] 必须是对象");
                continue;
            }
            Object key = item.get("key");
            Object text = item.get("text");
            if (!(key instanceof String) || ((String) key).isBlank()) {
                issues.add(path + "[" + (i + 1) + "].key 必须是非空字符串");
            } else {
                keys.add((String) key);
            }
            if (!(text instanceof String) || ((String) text).isBlank()) {
                issues.add(path + "[" + (i + 1) + "].text 必须是非空字符串");
            }
        }
        return keys;
    }

    private Map<String, Object> requireObject(Map<String, Object> container, String key, String path, List<String> issues) {
        Object value = container.get(key);
        if (!(value instanceof Map<?, ?> map)) {
            issues.add(path + "." + key + " 必须是对象");
            return null;
        }
        return toStringObjectMap(map);
    }

    private void requireList(Map<String, Object> container, String key, String path, List<String> issues) {
        if (!(container.get(key) instanceof List<?>)) {
            issues.add(path + "." + key + " 必须是数组");
        }
    }

    private void requireStringList(Map<String, Object> container, String key, String path, List<String> issues) {
        Object value = container.get(key);
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            issues.add(path + "." + key + " 必须是字符串数组");
        }
    }

    private void requireString(Map<String, Object> container, String key, String path, List<String> issues, boolean allowEmpty) {
        Object value = container.get(key);
        if (!(value instanceof String text)) {
            issues.add(path + "." + key + " 必须是字符串");
            return;
        }
        if (!allowEmpty && text.isBlank()) {
            issues.add(path + "." + key + " 不能为空");
        }
    }

    private void requirePositiveNumber(Map<String, Object> container, String key, String path, List<String> issues) {
        BigDecimal value = toBigDecimal(container.get(key));
        if (value == null) {
            issues.add(path + "." + key + " 必须是数字");
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(path + "." + key + " 必须大于 0");
        }
    }

    private ExamQuestionDTO.QuestionVO toVO(ExamQuestion question) {
        ExamQuestionDTO.QuestionVO vo = new ExamQuestionDTO.QuestionVO();
        vo.setId(question.getId());
        vo.setSourceQuestionId(question.getSourceQuestionId());
        vo.setType(question.getType());
        vo.setStem(question.getStem());
        vo.setScore(question.getScore());
        vo.setDifficulty(question.getDifficulty());
        vo.setKnowledgePoints(fromJson(question.getKnowledgePointsJson()));
        vo.setTags(fromJson(question.getTagsJson()));
        vo.setBody(fromJson(question.getBodyJson()));
        vo.setAnswer(fromJson(question.getAnswerJson()));
        vo.setAnalysis(question.getAnalysis());
        vo.setScoring(fromJson(question.getScoringJson()));
        vo.setSourceBasis(fromJson(question.getSourceBasisJson()));
        vo.setRawQuestion(fromJson(question.getRawQuestionJson()));
        vo.setSourceAgent(question.getSourceAgent());
        vo.setSourceTitle(question.getSourceTitle());
        vo.setCreatedBy(question.getCreatedBy());
        vo.setCreateTime(question.getCreateTime());
        vo.setUpdateTime(question.getUpdateTime());
        return vo;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题目 JSON 序列化失败");
        }
    }

    private Object fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Object>() {});
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toStringObjectMap(map);
        }
        return Map.of();
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number && !(value instanceof Boolean)) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }
}
