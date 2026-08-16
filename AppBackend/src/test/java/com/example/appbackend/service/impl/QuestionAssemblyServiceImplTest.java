package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRule;
import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import com.example.appbackend.dto.QuestionGenerationDTO.QuestionTypeOption;
import com.example.appbackend.entity.ExamQuestion;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionRepository;
import com.example.appbackend.service.QuestionGenerationMaterialParser;
import com.example.appbackend.service.QuestionGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionAssemblyServiceImplTest {

    @Mock ExamQuestionRepository questionRepository;
    @Mock QuestionGenerationService questionGenerationService;
    @Mock QuestionGenerationMaterialParser materialParser;
    @Mock PythonAiProxyService pythonAiProxyService;

    private QuestionAssemblyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionAssemblyServiceImpl(
                questionRepository,
                questionGenerationService,
                materialParser,
                pythonAiProxyService,
                new ObjectMapper());
    }

    @Test
    void hybridUsesVisibleExistingQuestionsAndCommitsGeneratedQuestionsPrivately() {
        when(questionRepository.findVisibleActiveCandidates("single_choice", "easy", 7L))
                .thenReturn(List.of(existingQuestion(101L, "已有题")));
        when(questionGenerationService.getOptions("Bearer token")).thenReturn(options());
        when(materialParser.parse("text", null, "Java 并发"))
                .thenReturn(new ParsedMaterial("Java 并发", null, "文本材料"));
        when(questionGenerationService.generate(any(), eq("Bearer token")))
                .thenReturn(generated("proof-1", "新生成题"));
        ExamQuestionDTO.ImportResponse imported = new ExamQuestionDTO.ImportResponse();
        imported.setQuestionIds(List.of(501L));
        when(questionGenerationService.importGeneratedPrivate(any(), eq(7L))).thenReturn(imported);

        var draft = service.generate(request("hybrid", "text", 2), null, 7L, "Bearer token");
        var committed = service.commitPrivate(draft.getDraftId(), 7L);

        assertEquals(1, draft.getExistingCount());
        assertEquals(1, draft.getGeneratedCount());
        assertEquals(0, draft.getMissingCount());
        assertThat(draft.getQuestions()).extracting(item -> item.getOrigin())
                .containsExactly("existing", "generated");
        assertEquals(List.of(501L), committed.getQuestionIds());
        ArgumentCaptor<com.example.appbackend.dto.QuestionGenerationDTO.GeneratedImportRequest> captor =
                ArgumentCaptor.forClass(com.example.appbackend.dto.QuestionGenerationDTO.GeneratedImportRequest.class);
        verify(questionGenerationService).importGeneratedPrivate(captor.capture(), eq(7L));
        assertEquals("proof-1", captor.getValue().getProof());
        assertThat(captor.getValue().getQuestions()).extracting(question -> question.get("stem"))
                .containsExactly("新生成题");
    }

    @Test
    void anotherUserCannotCommitSomeoneElsesDraft() {
        when(questionRepository.findVisibleActiveCandidates("single_choice", "easy", 7L))
                .thenReturn(List.of(existingQuestion(101L, "已有题")));

        var draft = service.generate(request("existing", null, 1), null, 7L, "Bearer token");
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.commitPrivate(draft.getDraftId(), 8L));

        assertEquals(404, error.getCode());
        verify(questionGenerationService, never()).importGeneratedPrivate(any(), any());
    }

    @Test
    void knowledgeAgentCanProvideSharedBasisForQuestionAgent() {
        when(questionGenerationService.getOptions("Bearer token")).thenReturn(options());
        when(pythonAiProxyService.queryRag(any(), eq("Bearer token")))
                .thenReturn(Map.of("answer", "线程生命周期、同步与锁"));
        when(questionGenerationService.generate(any(), eq("Bearer token")))
                .thenReturn(generated("proof-knowledge", "知识点题"));

        var draft = service.generate(
                request("generate", "knowledge_agent", 1), null, 7L, "Bearer token");

        assertEquals("textbook_knowledge_agent", draft.getBasisAgent());
        assertEquals(1, draft.getGeneratedCount());
        verify(pythonAiProxyService).queryRag(any(), eq("Bearer token"));
    }

    private AssemblyRequest request(String mode, String basisMode, int quantity) {
        AssemblyRule rule = new AssemblyRule();
        rule.setType("single_choice");
        rule.setDifficulty("easy");
        rule.setQuantity(quantity);
        AssemblyRequest request = new AssemblyRequest();
        request.setMode(mode);
        request.setBasisMode(basisMode);
        request.setText("Java 并发");
        request.setTopic("Java 并发");
        request.setRules(List.of(rule));
        return request;
    }

    private OptionsResponse options() {
        QuestionTypeOption option = new QuestionTypeOption();
        option.setType("single_choice");
        option.setAgentName("textbook_question_single_choice_agent");
        option.setAvailable(true);
        OptionsResponse response = new OptionsResponse();
        response.setQuestionTypes(List.of(option));
        return response;
    }

    private GenerationResponse generated(String proof, String stem) {
        GenerationResponse response = new GenerationResponse();
        response.setProof(proof);
        response.setQuestionType("single_choice");
        response.setAgentName("textbook_question_single_choice_agent");
        response.setValid(true);
        response.setQuestions(List.of(Map.of(
                "id", "generated-1",
                "type", "single_choice",
                "stem", stem,
                "score", 5,
                "difficulty", "easy")));
        return response;
    }

    private ExamQuestion existingQuestion(Long id, String stem) {
        ExamQuestion question = new ExamQuestion();
        question.setId(id);
        question.setType("single_choice");
        question.setStem(stem);
        question.setScore(new BigDecimal("5.00"));
        question.setDifficulty("easy");
        question.setRawQuestionJson("{\"id\":\"existing-1\",\"type\":\"single_choice\",\"stem\":\""
                + stem + "\",\"score\":5,\"difficulty\":\"easy\"}");
        return question;
    }
}
