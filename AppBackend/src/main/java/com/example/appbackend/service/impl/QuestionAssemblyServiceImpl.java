package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyOptions;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyQuestion;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRule;
import com.example.appbackend.dto.QuestionAssemblyDTO.PrivateCommitResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.GeneratedImportRequest;
import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import com.example.appbackend.dto.QuestionGenerationDTO.QuestionTypeOption;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.QuestionAssemblyService;
import com.example.appbackend.service.QuestionGenerationMaterialParser;
import com.example.appbackend.service.QuestionGenerationService;
import com.example.appbackend.service.QuestionGenerationService.GenerationCommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuestionAssemblyServiceImpl implements QuestionAssemblyService {

    private static final Set<String> MODES = Set.of("existing", "generate", "hybrid");
    private static final Set<String> BASIS_MODES = Set.of(
            "text", "file", "uploaded_question_bank", "knowledge_agent");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final String KNOWLEDGE_AGENT = "textbook_knowledge_agent";
    private static final long DRAFT_TTL_SECONDS = 15 * 60;

    private final ExamQuestionRepository questionRepository;
    private final QuestionGenerationService questionGenerationService;
    private final QuestionGenerationMaterialParser materialParser;
    private final PythonAiProxyService pythonAiProxyService;
    private final ObjectMapper objectMapper;
    private final Map<String, AssemblyDraft> drafts = new ConcurrentHashMap<>();

    public QuestionAssemblyServiceImpl(
            ExamQuestionRepository questionRepository,
            QuestionGenerationService questionGenerationService,
            QuestionGenerationMaterialParser materialParser,
            PythonAiProxyService pythonAiProxyService,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.questionGenerationService = questionGenerationService;
        this.materialParser = materialParser;
        this.pythonAiProxyService = pythonAiProxyService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssemblyOptions options(String authorization) {
        AssemblyOptions options = new AssemblyOptions();
        options.setQuestionTypes(questionGenerationService.getOptions(authorization).getQuestionTypes());
        return options;
    }

    @Override
    public AssemblyResponse generate(
            AssemblyRequest request, MultipartFile file, Long userId, String authorization) {
        requireUser(userId);
        cleanupExpiredDrafts();
        String mode = normalizeMode(request.getMode());
        validateRules(request.getRules());

        Map<String, QuestionTypeOption> generationOptions = new HashMap<>();
        if (!"existing".equals(mode)) {
            for (QuestionTypeOption option : questionGenerationService
                    .getOptions(authorization).getQuestionTypes()) {
                generationOptions.put(option.getType(), option);
            }
        }

        AssemblyResponse response = new AssemblyResponse();
        response.setMode(mode);
        response.setBasisMode(normalizeOptional(request.getBasisMode()));
        response.setRequestedCount(request.getRules().stream()
                .mapToInt(rule -> rule.getQuantity() == null ? 0 : rule.getQuantity()).sum());

        List<AssemblyQuestion> assembled = new ArrayList<>();
        List<GeneratedBatch> generatedBatches = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        Set<String> normalizedStems = new HashSet<>();
        Map<AssemblyRule, Integer> gaps = new LinkedHashMap<>();

        for (AssemblyRule rule : request.getRules()) {
            int selected = 0;
            if (!"generate".equals(mode)) {
                List<ExamQuestion> candidates = questionRepository.findVisibleActiveCandidates(
                        rule.getType(), normalizeOptional(rule.getDifficulty()), userId);
                for (ExamQuestion candidate : candidates) {
                    if (selected >= rule.getQuantity()) {
                        break;
                    }
                    String stemKey = normalizeStem(candidate.getStem());
                    if (!selectedIds.add(candidate.getId()) || !normalizedStems.add(stemKey)) {
                        continue;
                    }
                    assembled.add(existingQuestion(candidate));
                    selected++;
                }
            }
            int gap = "existing".equals(mode) ? 0 : Math.max(0, rule.getQuantity() - selected);
            gaps.put(rule, gap);
        }

        boolean requiresGeneration = gaps.values().stream().anyMatch(gap -> gap > 0);
        Basis basis = requiresGeneration
                ? resolveBasis(request, file, authorization)
                : new Basis("", firstNonBlank(request.getSourceTitle(), request.getTopic(), "系统题库编排"), "");
        response.setBasisAgent(basis.agentName());
        response.setSourceTitle(basis.sourceTitle());

        if (requiresGeneration) {
            for (Map.Entry<AssemblyRule, Integer> entry : gaps.entrySet()) {
                AssemblyRule rule = entry.getKey();
                int gap = entry.getValue();
                if (gap <= 0) {
                    continue;
                }
                QuestionTypeOption option = generationOptions.get(rule.getType());
                if (option == null || !Boolean.TRUE.equals(option.getAvailable())) {
                    response.getIssues().add(rule.getType() + " 没有可用的题型智能体，缺少 " + gap + " 道");
                    continue;
                }
                try {
                    GenerationResponse generated = questionGenerationService.generate(
                            new GenerationCommand(
                                    "text", null, basis.text(), rule.getType(), gap,
                                    normalizeOptional(rule.getDifficulty()), basis.sourceTitle()),
                            authorization);
                    response.getWarnings().addAll(generated.getWarnings() == null
                            ? List.of() : generated.getWarnings());
                    if (!Boolean.TRUE.equals(generated.getValid())) {
                        response.getIssues().addAll(generated.getIssues() == null
                                ? List.of(rule.getType() + " 生成失败") : generated.getIssues());
                        continue;
                    }
                    List<Map<String, Object>> accepted = new ArrayList<>();
                    for (Map<String, Object> question : generated.getQuestions()) {
                        if (accepted.size() >= gap) {
                            break;
                        }
                        String stemKey = normalizeStem(String.valueOf(question.getOrDefault("stem", "")));
                        if (!normalizedStems.add(stemKey)) {
                            response.getWarnings().add(rule.getType() + " 中发现重复题干，已跳过");
                            continue;
                        }
                        accepted.add(question);
                        assembled.add(generatedQuestion(question, generated.getAgentName()));
                    }
                    if (!accepted.isEmpty()) {
                        generatedBatches.add(new GeneratedBatch(
                                generated.getProof(), rule.getType(), accepted,
                                generated.getMissingInfo() == null ? List.of() : generated.getMissingInfo()));
                    }
                } catch (Exception error) {
                    response.getIssues().add(rule.getType() + " 智能体调用失败：" + safeMessage(error));
                }
            }
        }

        response.setQuestions(assembled);
        response.setExistingCount((int) assembled.stream()
                .filter(item -> "existing".equals(item.getOrigin())).count());
        response.setGeneratedCount((int) assembled.stream()
                .filter(item -> "generated".equals(item.getOrigin())).count());
        response.setMissingCount(Math.max(0, response.getRequestedCount() - assembled.size()));
        if (response.getMissingCount() > 0) {
            response.getIssues().add("当前仍缺少 " + response.getMissingCount() + " 道题");
        }

        String draftId = UUID.randomUUID().toString();
        response.setDraftId(draftId);
        drafts.put(draftId, new AssemblyDraft(
                userId, generatedBatches, Instant.now().plusSeconds(DRAFT_TTL_SECONDS)));
        return response;
    }

    @Override
    @Transactional
    public PrivateCommitResponse commitPrivate(String draftId, Long userId) {
        requireUser(userId);
        cleanupExpiredDrafts();
        AssemblyDraft draft = drafts.get(draftId);
        if (draft == null || !draft.ownerUserId().equals(userId)) {
            throw new BusinessException(Result.NOT_FOUND_CODE, "题库草稿不存在或已过期");
        }

        List<Long> importedIds = new ArrayList<>();
        for (GeneratedBatch batch : draft.generatedBatches()) {
            GeneratedImportRequest request = new GeneratedImportRequest();
            request.setProof(batch.proof());
            request.setQuestions(batch.questions());
            request.setMissingInfo(batch.missingInfo());
            ExamQuestionDTO.ImportResponse imported = questionGenerationService
                    .importGeneratedPrivate(request, userId);
            importedIds.addAll(imported.getQuestionIds());
        }
        drafts.remove(draftId, draft);
        PrivateCommitResponse response = new PrivateCommitResponse();
        response.setDraftId(draftId);
        response.setImportedCount(importedIds.size());
        response.setQuestionIds(importedIds);
        return response;
    }

    private Basis resolveBasis(AssemblyRequest request, MultipartFile file, String authorization) {
        String basisMode = normalizeOptional(request.getBasisMode());
        if (!BASIS_MODES.contains(basisMode)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "生成依据类型不合法");
        }
        if ("knowledge_agent".equals(basisMode)) {
            String topic = firstNonBlank(request.getTopic(), request.getText());
            if (!StringUtils.hasText(topic)) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "请填写要提取知识点的主题");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("agentName", KNOWLEDGE_AGENT);
            payload.put("input", "请整理以下主题的可靠知识点，作为后续题库生成依据：\n" + topic);
            Object raw = pythonAiProxyService.queryRag(payload, authorization);
            String answer = raw instanceof Map<?, ?> map && map.containsKey("answer")
                    ? String.valueOf(map.get("answer")) : "";
            if (!StringUtils.hasText(answer)) {
                throw new BusinessException(Result.ERROR_CODE, "知识点智能体未返回可用依据");
            }
            return new Basis(answer, firstNonBlank(request.getSourceTitle(), topic), KNOWLEDGE_AGENT);
        }

        String sourceType = sourceType(request, file);
        try {
            ParsedMaterial material = materialParser.parse(sourceType, file, request.getText());
            return new Basis(
                    material.text(),
                    firstNonBlank(request.getSourceTitle(), material.sourceTitle(), request.getTopic()),
                    "");
        } catch (IllegalArgumentException error) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, error.getMessage());
        }
    }

    private String sourceType(AssemblyRequest request, MultipartFile file) {
        if ("text".equals(request.getBasisMode()) && file == null) {
            return "text";
        }
        if (StringUtils.hasText(request.getSourceType())) {
            return request.getSourceType().trim().toLowerCase(Locale.ROOT);
        }
        String filename = file == null ? "" : String.valueOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        return filename.endsWith(".docx") ? "docx" : filename.endsWith(".txt") ? "txt" : "text";
    }

    private AssemblyQuestion existingQuestion(ExamQuestion source) {
        AssemblyQuestion item = new AssemblyQuestion();
        item.setOrigin("existing");
        item.setExistingQuestionId(source.getId());
        item.setType(source.getType());
        item.setQuestion(readQuestion(source));
        return item;
    }

    private AssemblyQuestion generatedQuestion(Map<String, Object> question, String agentName) {
        AssemblyQuestion item = new AssemblyQuestion();
        item.setOrigin("generated");
        item.setGeneratedBy(agentName);
        item.setType(String.valueOf(question.getOrDefault("type", "")));
        item.setQuestion(new LinkedHashMap<>(question));
        return item;
    }

    private Map<String, Object> readQuestion(ExamQuestion source) {
        try {
            return objectMapper.readValue(
                    source.getRawQuestionJson(), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("id", source.getSourceQuestionId());
            fallback.put("type", source.getType());
            fallback.put("stem", source.getStem());
            fallback.put("score", source.getScore());
            fallback.put("difficulty", source.getDifficulty());
            fallback.put("analysis", source.getAnalysis());
            return fallback;
        }
    }

    private void validateRules(List<AssemblyRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请至少选择一种题型");
        }
        int total = 0;
        for (AssemblyRule rule : rules) {
            if (rule == null || !StringUtils.hasText(rule.getType())) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "题型不能为空");
            }
            if (rule.getQuantity() == null || rule.getQuantity() < 1 || rule.getQuantity() > 100) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "每种题型数量必须在 1 到 100 之间");
            }
            String difficulty = normalizeOptional(rule.getDifficulty());
            if (StringUtils.hasText(difficulty) && !DIFFICULTIES.contains(difficulty)) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "难度必须是 easy、medium 或 hard");
            }
            total += rule.getQuantity();
        }
        if (total > 200) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "单次编排题目总数不能超过 200");
        }
    }

    private String normalizeMode(String mode) {
        String normalized = normalizeOptional(mode);
        if (!MODES.contains(normalized)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "编排模式不合法");
        }
        return normalized;
    }

    private void cleanupExpiredDrafts() {
        Instant now = Instant.now();
        drafts.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
    }

    private String normalizeStem(String value) {
        return String.valueOf(value == null ? "" : value)
                .replaceAll("[\\s\\p{Punct}，。！？；：、]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "题库编排";
    }

    private String safeMessage(Exception error) {
        if (error instanceof BusinessException) {
            String message = error.getMessage();
            return StringUtils.hasText(message) ? message : "服务暂不可用";
        }
        return "服务暂不可用";
    }

    private record Basis(String text, String sourceTitle, String agentName) {
    }

    private record GeneratedBatch(
            String proof, String questionType, List<Map<String, Object>> questions, List<String> missingInfo) {
    }

    private record AssemblyDraft(
            Long ownerUserId, List<GeneratedBatch> generatedBatches, Instant expiresAt) {
    }
}
