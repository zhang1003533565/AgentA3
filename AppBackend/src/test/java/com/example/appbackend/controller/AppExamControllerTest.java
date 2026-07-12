package com.example.appbackend.controller;

import com.example.appbackend.dto.AppExamDTO;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.AppExamService;
import com.example.appbackend.service.exampaper.AppExamPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppExamControllerTest {
    private AppExamService service;
    private AppExamPdfService pdfService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AppExamService.class);
        pdfService = mock(AppExamPdfService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AppExamController(service, pdfService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void endpointsRequireAuthenticatedRequestAttribute() throws Exception {
        mvc.perform(get("/api/app/exam-papers")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/app/exam-papers/7/attempts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/app/exam-attempts/4/result")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, pdfService);
    }

    @Test
    void answerDelegatesOnlyAuthenticatedUserAndValidatedBody() throws Exception {
        when(service.saveAnswer(eq(4L), eq(8L), eq(42L), any(), any())).thenReturn(new AppExamDTO.SavedAnswer());
        mvc.perform(put("/api/app/exam-attempts/4/answers/8")
                        .requestAttr("userId", 42L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerJson\":\"{\\\"selectedOption\\\":\\\"A\\\"}\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).saveAnswer(eq(4L), eq(8L), eq(42L), any(), any());
    }

    @Test
    void missingAnswerVersionIsRejectedWithoutServiceInteraction() throws Exception {
        mvc.perform(put("/api/app/exam-attempts/4/answers/8")
                        .requestAttr("userId", 42L).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerJson\":\"{}\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, pdfService);
    }

    @Test
    void listBoundsAreValidatedBeforeService() throws Exception {
        mvc.perform(get("/api/app/exam-papers").requestAttr("userId", 42L).param("size", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, pdfService);
    }

    @Test
    void pdfUsesAppPermissionServiceAndReturnsOnlyPdf() throws Exception {
        when(pdfService.downloadBlankPaper(7L, 42L))
                .thenReturn(new AppExamPdfService.PdfFile("期末/考试", new byte[]{1, 2}));
        mvc.perform(get("/api/app/exam-papers/7/pdf").requestAttr("userId", 42L))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(new byte[]{1, 2}));
        verify(pdfService).downloadBlankPaper(7L, 42L);
        verifyNoInteractions(service);
    }
}
