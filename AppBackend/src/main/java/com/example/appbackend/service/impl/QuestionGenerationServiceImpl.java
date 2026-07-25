package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import com.example.appbackend.dto.QuestionGenerationDTO.QuestionTypeOption;
import com.example.appbackend.dto.QuestionGenerationDTO.GeneratedImportRequest;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamQuestionService;
import com.example.appbackend.service.QuestionGenerationMaterialParser;
import com.example.appbackend.service.QuestionGenerationService;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class QuestionGenerationServiceImpl implements QuestionGenerationService {
    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationServiceImpl.class);
    private static final long PROOF_TTL_SECONDS = 15 * 60;
    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^\\r\\n]*?]\\([^\\r\\n]*?\\)");
    private static final Pattern HTML_IMAGE = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern STANDALONE_OSS_IMAGE_PATH = Pattern.compile(
            "(?im)^\\s*(?:\\./)?oss/file/\\S+\\s*$");
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile(
            "(?:\\r?\\n[ \\t]*){3,}");

    private static final String MAPPING_PREFIX = "ai.question-generation.agent.";
    private static final List<String> QUESTION_TYPES = List.of(
            "single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer",
            "calculation", "programming");

    private final SystemConfigService systemConfigService;
    private final PythonAiProxyService pythonAiProxyService;
    private final QuestionGenerationMaterialParser materialParser;
    private final ExamQuestionService examQuestionService;
    private final ObjectMapper objectMapper;
    private final Map<String, GenerationProof> proofs = new ConcurrentHashMap<>();

    public QuestionGenerationServiceImpl(SystemConfigService systemConfigService,
                                         PythonAiProxyService pythonAiProxyService,
                                         QuestionGenerationMaterialParser materialParser,
                                         ExamQuestionService examQuestionService,
                                         ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.pythonAiProxyService = pythonAiProxyService;
        this.materialParser = materialParser;
        this.examQuestionService = examQuestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public OptionsResponse getOptions(String authorization) {
        Map<String, PythonAiProxyService.AgentDescriptor> resolvedCatalog;
        try {
            resolvedCatalog = pythonAiProxyService.getQuestionGenerationAgentCatalog(authorization);
        } catch (RuntimeException error) {
            log.warn("question generation agent catalog unavailable: {}", error.getMessage());
            resolvedCatalog = Map.of();
        }
        final Map<String, PythonAiProxyService.AgentDescriptor> catalog = resolvedCatalog;
        OptionsResponse response = new OptionsResponse();
        response.setQuestionTypes(QUESTION_TYPES.stream()
                .map(type -> resolveOption(type, catalog))
                .toList());
        return response;
    }

    @Override
    public GenerationResponse generate(GenerationCommand command, String authorization) {
        long started = System.nanoTime();
        if (command == null) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "生成命令不能为空");
        }
        if (!QUESTION_TYPES.contains(command.questionType())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题型不合法");
        }
        if (command.maxQuestions() != null && command.maxQuestions() < 1) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "最大题量必须大于 0");
        }

        Map<String, PythonAiProxyService.AgentDescriptor> catalog =
                pythonAiProxyService.getQuestionGenerationAgentCatalog(authorization);
        QuestionTypeOption option = resolveOption(command.questionType(), catalog);
        if (!Boolean.TRUE.equals(option.getAvailable())) {
            String reason = "未配置题型智能体".equals(option.getUnavailableReason())
                    ? "未配置生成智能体" : option.getUnavailableReason();
            throw new BusinessException(Result.BAD_REQUEST_CODE,
                    "该题型" + reason + "，请到智能体设置完成映射");
        }

        ParsedMaterial material = materialParser.parse(command.sourceType(), command.file(), command.text());
        String preparedMaterial = prepareMaterialForGeneration(material.text());
        String sourceTitle = StringUtils.hasText(command.sourceTitle())
                ? command.sourceTitle().trim() : material.sourceTitle();
        GenerationResponse response = baseResponse(command, option, material, sourceTitle);
        String answer = pythonAiProxyService.queryQuestionGeneration(
                new PythonAiProxyService.QuestionGenerationPayload(
                        option.getAgentName(), buildInput(command, preparedMaterial),
                        command.maxQuestions(), command.difficulty()),
                authorization);

        ExamQuestionDTO.ImportRequest importRequest;
        try {
            importRequest = objectMapper.readValue(answer, ExamQuestionDTO.ImportRequest.class);
        } catch (JsonProcessingException exception) {
            return invalidJsonResponse(response);
        }
        if (importRequest == null) {
            return invalidJsonResponse(response);
        }

        importRequest.setSourceAgent(option.getAgentName());
        importRequest.setSourceTitle(sourceTitle);
        importRequest.setSourceScene("question_generation");
        ExamQuestionDTO.ReviewResponse review = examQuestionService.review(importRequest, command.questionType());
        List<Map<String, Object>> questions = importRequest.getQuestions() == null
                ? List.of() : importRequest.getQuestions();
        List<String> issues = new ArrayList<>(review.getIssues() == null ? List.of() : review.getIssues());
        if (questions.isEmpty()) {
            issues.add("未生成题目，当前结果不可导入");
        }
        if (command.maxQuestions() != null && questions.size() > command.maxQuestions()) {
            issues.add("生成题量 " + questions.size() + " 超过最大题量 " + command.maxQuestions());
        }

        response.setQuestions(questions);
        response.setMissingInfo(importRequest.getMissingInfo() == null ? List.of() : importRequest.getMissingInfo());
        response.setGeneratedCount(questions.size());
        response.setIssues(issues);
        response.setWarnings(review.getWarnings() == null ? List.of() : review.getWarnings());
        response.setValid(Boolean.TRUE.equals(review.getValid()) && issues.isEmpty());
        String proof = UUID.randomUUID().toString();
        proofs.put(proof, new GenerationProof(command.questionType(), option.getAgentName(), sourceTitle,
                Instant.now().plusSeconds(PROOF_TTL_SECONDS)));
        response.setProof(proof);
        response.setModel(option.getModel());
        response.setMaterialCharacters(preparedMaterial.length());
        response.setMaterialSummary(preparedMaterial.length() + " 字符；已保留图片说明并过滤不可读取的图片路径");
        log.info("question_generation type={} agent={} model={} chars={} max={} actual={} durationMs={} status={}",
                command.questionType(), option.getAgentName(), option.getModel(), preparedMaterial.length(),
                command.maxQuestions(), questions.size(), (System.nanoTime() - started) / 1_000_000,
                response.getValid() ? "valid" : "invalid");
        return response;
    }

    @Override
    public ExamQuestionDTO.ImportResponse importGenerated(GeneratedImportRequest request, Long userId) {
        return importGenerated(request, userId, true);
    }

    @Override
    public ExamQuestionDTO.ImportResponse importGeneratedPrivate(GeneratedImportRequest request, Long userId) {
        return importGenerated(request, userId, false);
    }

    private ExamQuestionDTO.ImportResponse importGenerated(
            GeneratedImportRequest request, Long userId, boolean publicImport) {
        GenerationProof proof = proofs.remove(request.getProof());
        if (proof == null || proof.expiresAt().isBefore(Instant.now())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "生成凭证无效、已过期或已使用");
        }
        ExamQuestionDTO.ImportRequest trusted = new ExamQuestionDTO.ImportRequest();
        trusted.setQuestions(request.getQuestions());
        trusted.setMissingInfo(request.getMissingInfo());
        trusted.setSourceAgent(proof.agentName());
        trusted.setSourceTitle(proof.sourceTitle());
        trusted.setSourceScene(publicImport ? "question_generation" : "user_question_generation");
        return publicImport
                ? examQuestionService.importPublicQuestions(trusted, proof.questionType(), userId)
                : examQuestionService.importQuestions(trusted, proof.questionType(), userId);
    }

    private GenerationResponse invalidJsonResponse(GenerationResponse response) {
        response.setValid(false);
        response.setIssues(List.of("智能体输出不是合法的题库 JSON"));
        response.setGeneratedCount(0);
        return response;
    }

    private GenerationResponse baseResponse(GenerationCommand command,
                                            QuestionTypeOption option,
                                            ParsedMaterial material,
                                            String sourceTitle) {
        GenerationResponse response = new GenerationResponse();
        response.setQuestionType(command.questionType());
        response.setAgentName(option.getAgentName());
        response.setAgentRole(option.getAgentRole());
        response.setSourceTitle(sourceTitle);
        response.setOriginalFilename(material.originalFilename());
        response.setMaxQuestions(command.maxQuestions());
        return response;
    }

    private String buildInput(GenerationCommand command, String material) {
        String quantity = command.maxQuestions() == null
                ? "由有效知识点决定题量；资料不足时宁可少出题或不出题，不得为了凑数降低质量。"
                : "最多生成 " + command.maxQuestions() + " 道题；这是上限，不是必须达到的数量。";
        String difficulty = StringUtils.hasText(command.difficulty())
                ? "\n指定难度：" + command.difficulty().trim() : "";
        return "请严格依据以下材料生成 " + command.questionType() + " 题型的标准题库 JSON。"
                + "顶层只能包含 questions 数组和 missingInfo 数组；不得脱离材料或重复出题。\n"
                + quantity + difficulty + "\n材料：\n" + material;
    }

    static String prepareMaterialForGeneration(String material) {
        if (!StringUtils.hasText(material)) {
            return material;
        }
        String prepared = MARKDOWN_IMAGE.matcher(material).replaceAll("");
        prepared = HTML_IMAGE.matcher(prepared).replaceAll("");
        prepared = STANDALONE_OSS_IMAGE_PATH.matcher(prepared).replaceAll("");
        prepared = EXCESSIVE_BLANK_LINES.matcher(prepared).replaceAll("\n\n");
        return prepared.trim();
    }

    private QuestionTypeOption resolveOption(
            String type,
            Map<String, PythonAiProxyService.AgentDescriptor> catalog
    ) {
        QuestionTypeOption option = new QuestionTypeOption();
        option.setType(type);
        String agentName = systemConfigService.getValue(MAPPING_PREFIX + type, "");
        if (!StringUtils.hasText(agentName)) {
            unavailable(option, "未配置题型智能体");
            return option;
        }

        agentName = agentName.trim();
        option.setAgentName(agentName);
        PythonAiProxyService.AgentDescriptor descriptor = catalog.get(agentName);
        if (descriptor == null) {
            unavailable(option, "配置的智能体不存在");
        } else if (!descriptor.enabled()) {
            option.setAgentRole(descriptor.role());
            unavailable(option, "配置的智能体已停用");
        } else if (!StringUtils.hasText(descriptor.modelBinding())) {
            option.setAgentRole(descriptor.role());
            unavailable(option, "配置的智能体未绑定已测试模型");
        } else {
            option.setAgentRole(descriptor.role());
            if (!isTestedModel(descriptor.modelBinding())) {
                unavailable(option, "配置的智能体模型未通过服务端测试或配置已修改");
                return option;
            }
            option.setModel(systemConfigService.getValue(descriptor.modelBinding() + ".model", "").trim());
            option.setAvailable(true);
            option.setUnavailableReason(null);
        }
        return option;
    }

    private void unavailable(QuestionTypeOption option, String reason) {
        option.setAvailable(false);
        option.setUnavailableReason(reason);
    }

    private boolean isTestedModel(String prefix) {
        if (!StringUtils.hasText(prefix)) return false;
        String provider = systemConfigService.getValue(prefix + ".provider", "").trim();
        String baseUrl = systemConfigService.getValue(prefix + ".base-url", "").trim();
        String apiKey = systemConfigService.getValue(prefix + ".api-key", "").trim();
        String model = systemConfigService.getValue(prefix + ".model", "").trim();
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(apiKey) || !StringUtils.hasText(model)) return false;
        return fingerprint(provider, baseUrl, apiKey, model)
                .equals(systemConfigService.getValue(prefix + ".tested-fingerprint", ""));
    }

    public static String fingerprint(String provider, String baseUrl, String apiKey, String model) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(
                    String.join("\u0000", provider, baseUrl, apiKey, model)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record GenerationProof(String questionType, String agentName, String sourceTitle, Instant expiresAt) {}
}
