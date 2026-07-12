package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.QuestionTypeOption;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamQuestionService;
import com.example.appbackend.service.QuestionGenerationMaterialParser;
import com.example.appbackend.service.QuestionGenerationService.GenerationCommand;
import com.example.appbackend.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationServiceImplTest {

    private static final String AUTHORIZATION = "Bearer test-token";

    private final Map<String, String> config = new HashMap<>();
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final PythonAiProxyService pythonAiProxyService = mock(PythonAiProxyService.class);
    private final QuestionGenerationMaterialParser materialParser = mock(QuestionGenerationMaterialParser.class);
    private final ExamQuestionService examQuestionService = mock(ExamQuestionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(systemConfigService.getValue(any(), any())).thenAnswer(invocation ->
                config.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
        when(materialParser.parse(any(), any(), any())).thenReturn(
                new com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial(
                        "栈遵循后进先出原则。", null, "文本材料"));
    }

    @Test
    void returnsFiveQuestionTypesAndMarksValidMappingAvailable() {
        map("single_choice", "choice_agent");
        catalog(Map.of("choice_agent", descriptor("choice_agent", "选择题专家", true, "ai.service.text.choice")));
        testedModel("ai.service.text.choice");

        OptionsResponse response = service().getOptions(AUTHORIZATION);

        assertThat(response.getQuestionTypes()).extracting(QuestionTypeOption::getType)
                .containsExactly("single_choice", "multiple_choice", "true_false", "fill_blank", "short_answer");
        assertThat(option(response, "single_choice"))
                .extracting(QuestionTypeOption::getAgentName, QuestionTypeOption::getAgentRole,
                        QuestionTypeOption::getAvailable, QuestionTypeOption::getUnavailableReason)
                .containsExactly("choice_agent", "选择题专家", true, null);
    }

    @Test
    void modelBecomesUnavailableWhenPersistedConfigurationChangesAfterTest() {
        map("single_choice", "choice_agent");
        catalog(Map.of("choice_agent", descriptor("choice_agent", "选择题专家", true, "ai.service.text.choice")));
        testedModel("ai.service.text.choice");
        config.put("ai.service.text.choice.model", "changed-model");

        QuestionTypeOption option = option(service().getOptions(AUTHORIZATION), "single_choice");

        assertThat(option.getAvailable()).isFalse();
        assertThat(option.getUnavailableReason()).contains("配置已修改");
    }

    @Test
    void marksMissingMappingUnavailableWithoutFallingBackToAnotherAgent() {
        catalog(Map.of("some_agent", descriptor("some_agent", "任意专家", true, "ai.service.text.some")));
        testedModel("ai.service.text.some");

        QuestionTypeOption option = option(service().getOptions(AUTHORIZATION), "multiple_choice");

        assertThat(option.getAvailable()).isFalse();
        assertThat(option.getAgentName()).isNull();
        assertThat(option.getUnavailableReason()).contains("未配置题型智能体");
    }

    @Test
    void marksMappingToUnknownAgentUnavailable() {
        map("true_false", "missing_agent");
        catalog(Map.of());
        assertThat(option(service().getOptions(AUTHORIZATION), "true_false").getUnavailableReason())
                .contains("智能体不存在");
    }

    @Test
    void marksDisabledAgentUnavailable() {
        map("fill_blank", "blank_agent");
        catalog(Map.of("blank_agent", descriptor("blank_agent", "填空题专家", false, "model")));
        assertThat(option(service().getOptions(AUTHORIZATION), "fill_blank").getUnavailableReason())
                .contains("智能体已停用");
    }

    @Test
    void marksAgentWithoutTestedModelBindingUnavailable() {
        map("short_answer", "short_agent");
        catalog(Map.of("short_agent", descriptor("short_agent", "简答题专家", true, null)));
        assertThat(option(service().getOptions(AUTHORIZATION), "short_answer").getUnavailableReason())
                .contains("未绑定已测试模型");
    }

    @Test
    void missingMappingDoesNotCallAi() {
        catalog(Map.of("leader_agent", descriptor("leader_agent", "总控", true, "model")));

        assertThatThrownBy(() -> service().generate(command(null, null), AUTHORIZATION))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置生成智能体");

        verify(pythonAiProxyService, never()).queryQuestionGeneration(any(), any());
    }

    @Test
    void optionalMaximumUsesConfiguredAgentAndLetsKnowledgePointsDetermineCount() {
        availableAgent();
        stubReview(true, List.of());
        when(pythonAiProxyService.queryQuestionGeneration(any(), eq(AUTHORIZATION)))
                .thenReturn(json(List.of(question("single_choice", "medium")), List.of()));

        service().generate(command(null, null), AUTHORIZATION);

        verify(pythonAiProxyService).queryQuestionGeneration(
                org.mockito.ArgumentMatchers.argThat(payload ->
                        payload.agentName().equals("configured_agent")
                                && payload.maxQuestions() == null
                                && payload.input().contains("由有效知识点决定题量")
                                && !payload.input().contains("必须生成")),
                eq(AUTHORIZATION));
    }

    @Test
    void specifiedDifficultyIsPassedToPythonPayload() {
        availableAgent();
        stubReview(true, List.of());
        when(pythonAiProxyService.queryQuestionGeneration(any(), any()))
                .thenReturn(json(List.of(question("single_choice", "hard")), List.of()));

        service().generate(command(3, "hard"), AUTHORIZATION);

        verify(pythonAiProxyService).queryQuestionGeneration(
                org.mockito.ArgumentMatchers.argThat(payload ->
                        "hard".equals(payload.difficulty()) && payload.input().contains("hard")),
                eq(AUTHORIZATION));
    }

    @Test
    void validJsonIsReviewedWithExpectedTypeAndReturned() {
        availableAgent();
        ExamQuestionDTO.ReviewResponse review = stubReview(true, List.of());
        when(pythonAiProxyService.queryQuestionGeneration(any(), any()))
                .thenReturn(json(List.of(question("single_choice", "medium")), List.of()));

        GenerationResponse response = service().generate(command(2, null), AUTHORIZATION);

        ArgumentCaptor<ExamQuestionDTO.ImportRequest> request = ArgumentCaptor.forClass(ExamQuestionDTO.ImportRequest.class);
        verify(examQuestionService).review(request.capture(), eq("single_choice"));
        assertThat(request.getValue().getSourceAgent()).isEqualTo("configured_agent");
        assertThat(request.getValue().getSourceTitle()).isEqualTo("课程第一章");
        assertThat(request.getValue().getSourceScene()).isEqualTo("question_generation");
        assertThat(response.getValid()).isTrue();
        assertThat(response.getGeneratedCount()).isEqualTo(1);
        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getIssues()).isEqualTo(review.getIssues());
    }

    @Test
    void invalidJsonCannotBeImportedAndDoesNotReachReview() {
        availableAgent();
        when(pythonAiProxyService.queryQuestionGeneration(any(), any())).thenReturn("not-json");

        GenerationResponse response = service().generate(command(null, null), AUTHORIZATION);

        assertThat(response.getValid()).isFalse();
        assertThat(response.getIssues()).anyMatch(issue -> issue.contains("JSON"));
        verify(examQuestionService, never()).review(any(), any());
    }

    @Test
    void literalNullJsonCannotBeImportedAndDoesNotReachReview() {
        availableAgent();
        when(pythonAiProxyService.queryQuestionGeneration(any(), any())).thenReturn("null");

        GenerationResponse response = service().generate(command(null, null), AUTHORIZATION);

        assertThat(response.getValid()).isFalse();
        assertThat(response.getIssues()).containsExactly("智能体输出不是合法的题库 JSON");
        assertThat(response.getGeneratedCount()).isZero();
        verify(examQuestionService, never()).review(any(), any());
    }

    @Test
    void zeroQuestionsKeepsMissingInfoAndCannotBeImported() {
        availableAgent();
        stubReview(true, List.of());
        when(pythonAiProxyService.queryQuestionGeneration(any(), any()))
                .thenReturn(json(List.of(), List.of("材料缺少可验证知识点")));

        GenerationResponse response = service().generate(command(null, null), AUTHORIZATION);

        assertThat(response.getMissingInfo()).containsExactly("材料缺少可验证知识点");
        assertThat(response.getValid()).isFalse();
        assertThat(response.getIssues()).anyMatch(issue -> issue.contains("未生成题目"));
    }

    @Test
    void mixedQuestionTypesCannotBeImported() {
        availableAgent();
        stubReview(false, List.of("questions[2].type 必须是 single_choice，实际为 true_false"));
        when(pythonAiProxyService.queryQuestionGeneration(any(), any())).thenReturn(json(List.of(
                question("single_choice", "medium"), question("true_false", "medium")), List.of()));

        GenerationResponse response = service().generate(command(null, null), AUTHORIZATION);

        assertThat(response.getValid()).isFalse();
        assertThat(response.getIssues()).anyMatch(issue -> issue.contains("type 必须是 single_choice"));
        ArgumentCaptor<ExamQuestionDTO.ImportRequest> request =
                ArgumentCaptor.forClass(ExamQuestionDTO.ImportRequest.class);
        verify(examQuestionService).review(request.capture(), eq("single_choice"));
        assertThat(request.getValue().getQuestions()).hasSize(2);
        assertThat(request.getValue().getQuestions())
                .extracting(question -> question.get("type"))
                .containsExactly("single_choice", "true_false");
    }

    @Test
    void forwardsDocxTxtAndTextSourcesUnchangedToMaterialParser() {
        availableAgent();
        stubReview(true, List.of());
        when(pythonAiProxyService.queryQuestionGeneration(any(), any()))
                .thenReturn(json(List.of(question("single_choice", "medium")), List.of()));
        MockMultipartFile docx = new MockMultipartFile(
                "file", "lesson.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{1, 2, 3});
        MockMultipartFile txt = new MockMultipartFile(
                "file", "lesson.txt", "text/plain", "文本内容".getBytes());

        service().generate(new GenerationCommand(
                "docx", docx, "docx-fallback", "single_choice", null, null, null), AUTHORIZATION);
        service().generate(new GenerationCommand(
                "txt", txt, "txt-fallback", "single_choice", null, null, null), AUTHORIZATION);
        service().generate(new GenerationCommand(
                "text", null, "直接文本", "single_choice", null, null, null), AUTHORIZATION);

        verify(materialParser).parse("docx", docx, "docx-fallback");
        verify(materialParser).parse("txt", txt, "txt-fallback");
        verify(materialParser).parse("text", null, "直接文本");
    }

    @Test
    void exceedingMaximumAddsIssueWithoutTruncatingQuestions() {
        availableAgent();
        stubReview(true, List.of());
        when(pythonAiProxyService.queryQuestionGeneration(any(), any())).thenReturn(json(List.of(
                question("single_choice", "medium"), question("single_choice", "medium")), List.of()));

        GenerationResponse response = service().generate(command(1, null), AUTHORIZATION);

        assertThat(response.getQuestions()).hasSize(2);
        assertThat(response.getGeneratedCount()).isEqualTo(2);
        assertThat(response.getValid()).isFalse();
        assertThat(response.getIssues()).anyMatch(issue -> issue.contains("超过最大题量 1"));
    }

    @Test
    void generatedImportUsesServerMetadataAndConsumesProofOnce() {
        QuestionGenerationServiceImpl service = service();
        availableAgent();
        when(pythonAiProxyService.queryQuestionGeneration(any(), any()))
                .thenReturn(json(List.of(question("single_choice", "easy")), List.of()));
        stubReview(true, List.of());
        GenerationResponse generated = service.generate(command(null, null), AUTHORIZATION);
        var request = new com.example.appbackend.dto.QuestionGenerationDTO.GeneratedImportRequest();
        request.setProof(generated.getProof());
        request.setQuestions(generated.getQuestions());
        ExamQuestionDTO.ImportResponse imported = new ExamQuestionDTO.ImportResponse();
        when(examQuestionService.importQuestions(any(), eq("single_choice"), eq(9L))).thenReturn(imported);

        service.importGenerated(request, 9L);

        var captor = org.mockito.ArgumentCaptor.forClass(ExamQuestionDTO.ImportRequest.class);
        verify(examQuestionService).importQuestions(captor.capture(), eq("single_choice"), eq(9L));
        assertThat(captor.getValue().getSourceAgent()).isEqualTo("configured_agent");
        assertThat(captor.getValue().getSourceTitle()).isEqualTo("课程第一章");
        assertThat(captor.getValue().getSourceScene()).isEqualTo("question_generation");
        assertThatThrownBy(() -> service.importGenerated(request, 9L)).hasMessageContaining("已使用");
    }

    private QuestionGenerationServiceImpl service() {
        return new QuestionGenerationServiceImpl(systemConfigService, pythonAiProxyService,
                materialParser, examQuestionService, objectMapper);
    }

    private GenerationCommand command(Integer maxQuestions, String difficulty) {
        return new GenerationCommand("text", null, "栈遵循后进先出原则。",
                "single_choice", maxQuestions, difficulty, "课程第一章");
    }

    private void availableAgent() {
        map("single_choice", "configured_agent");
        catalog(Map.of("configured_agent", descriptor("configured_agent", "选择题专家", true, "ai.service.text.configured")));
        testedModel("ai.service.text.configured");
    }

    private ExamQuestionDTO.ReviewResponse stubReview(boolean valid, List<String> issues) {
        ExamQuestionDTO.ReviewResponse review = new ExamQuestionDTO.ReviewResponse();
        review.setValid(valid);
        review.setIssues(issues);
        review.setWarnings(List.of());
        review.setQuestionCount(1);
        review.setTypes(List.of("single_choice"));
        when(examQuestionService.review(any(), any())).thenReturn(review);
        return review;
    }

    private String json(List<Map<String, Object>> questions, List<String> missingInfo) {
        try {
            return objectMapper.writeValueAsString(Map.of("questions", questions, "missingInfo", missingInfo));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Map<String, Object> question(String type, String difficulty) {
        Map<String, Object> question = new HashMap<>();
        question.put("id", "q-1");
        question.put("type", type);
        question.put("stem", "栈的访问顺序是什么？");
        question.put("score", 2);
        question.put("difficulty", difficulty);
        question.put("knowledgePoints", List.of("栈"));
        question.put("tags", List.of());
        question.put("body", Map.of("options", List.of()));
        question.put("answer", Map.of("correct", "A"));
        question.put("analysis", "栈是后进先出结构。");
        question.put("scoring", Map.of("mode", "exact"));
        question.put("sourceBasis", List.of("栈遵循后进先出原则。"));
        return question;
    }

    private void map(String type, String agentName) {
        config.put("ai.question-generation.agent." + type, agentName);
    }

    private void catalog(Map<String, PythonAiProxyService.AgentDescriptor> catalog) {
        when(pythonAiProxyService.getQuestionGenerationAgentCatalog(AUTHORIZATION)).thenReturn(catalog);
    }

    private void testedModel(String prefix) {
        config.put(prefix + ".provider", "openai");
        config.put(prefix + ".base-url", "https://example.test/v1");
        config.put(prefix + ".api-key", "secret");
        config.put(prefix + ".model", "model-1");
        config.put(prefix + ".tested-fingerprint", QuestionGenerationServiceImpl.fingerprint(
                "openai", "https://example.test/v1", "secret", "model-1"));
    }

    private PythonAiProxyService.AgentDescriptor descriptor(String name, String role, boolean enabled, String modelBinding) {
        return new PythonAiProxyService.AgentDescriptor(name, role, enabled, modelBinding);
    }

    private QuestionTypeOption option(OptionsResponse response, String type) {
        return response.getQuestionTypes().stream().filter(candidate -> type.equals(candidate.getType()))
                .findFirst().orElseThrow();
    }
}
