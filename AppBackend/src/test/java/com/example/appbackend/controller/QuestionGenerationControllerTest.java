package com.example.appbackend.controller;

import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.QuestionGenerationService;
import com.example.appbackend.service.QuestionGenerationService.GenerationCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionGenerationControllerTest {

    private static final String AUTHORIZATION = "Bearer token";

    private QuestionGenerationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(QuestionGenerationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QuestionGenerationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void nonAdminCannotReadOptionsOrGenerate() throws Exception {
        mockMvc.perform(get("/api/exam/question-generation/options")
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/exam/question-generation/generate")
                        .param("sourceType", "text")
                        .param("text", "课程材料")
                        .param("questionType", "single_choice")
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void adminReadsOptionsWithUnmodifiedAuthorization() throws Exception {
        when(service.getOptions(AUTHORIZATION)).thenReturn(new OptionsResponse());

        mockMvc.perform(get("/api/exam/question-generation/options")
                        .requestAttr("role", "ADMIN")
                        .header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).getOptions(AUTHORIZATION);
    }

    @Test
    void adminGeneratesFromTextAndPassesAllFields() throws Exception {
        when(service.generate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AUTHORIZATION))).thenReturn(new GenerationResponse());

        mockMvc.perform(multipart("/api/exam/question-generation/generate")
                        .param("sourceType", "text")
                        .param("text", "课程材料")
                        .param("questionType", "single_choice")
                        .param("maxQuestions", "4")
                        .param("difficulty", "hard")
                        .param("sourceTitle", "第一章")
                        .requestAttr("role", "ADMIN")
                        .header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<GenerationCommand> command = ArgumentCaptor.forClass(GenerationCommand.class);
        verify(service).generate(command.capture(), org.mockito.ArgumentMatchers.eq(AUTHORIZATION));
        assertThat(command.getValue())
                .extracting(GenerationCommand::sourceType, GenerationCommand::text,
                        GenerationCommand::questionType, GenerationCommand::maxQuestions,
                        GenerationCommand::difficulty, GenerationCommand::sourceTitle)
                .containsExactly("text", "课程材料", "single_choice", 4, "hard", "第一章");
        assertThat(command.getValue().file()).isNull();
    }

    @Test
    void adminGeneratesFromDocxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "course.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx".getBytes());
        when(service.generate(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(AUTHORIZATION))).thenReturn(new GenerationResponse());

        mockMvc.perform(multipart("/api/exam/question-generation/generate")
                        .file(file)
                        .param("sourceType", "file")
                        .param("questionType", "short_answer")
                        .requestAttr("role", "ADMIN")
                        .header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk());

        ArgumentCaptor<GenerationCommand> command = ArgumentCaptor.forClass(GenerationCommand.class);
        verify(service).generate(command.capture(), org.mockito.ArgumentMatchers.eq(AUTHORIZATION));
        assertThat(command.getValue().file().getOriginalFilename()).isEqualTo("course.docx");
        assertThat(command.getValue().text()).isNull();
    }

    @Test
    void invalidSourceTypeReturnsBadRequestWithoutCallingService() throws Exception {
        assertInvalidRequest("sourceType", "url");
    }

    @Test
    void invalidQuestionTypeReturnsBadRequestWithoutCallingService() throws Exception {
        assertInvalidRequest("questionType", "essay");
    }

    @Test
    void invalidMaximumReturnsBadRequestWithoutCallingService() throws Exception {
        assertInvalidRequest("maxQuestions", "0");
    }

    private void assertInvalidRequest(String parameter, String value) throws Exception {
        var request = multipart("/api/exam/question-generation/generate")
                .param("sourceType", "text")
                .param("text", "课程材料")
                .param("questionType", "single_choice")
                .requestAttr("role", "ADMIN")
                .contentType(MediaType.MULTIPART_FORM_DATA);
        request.param(parameter, value);

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(service);
    }
}
