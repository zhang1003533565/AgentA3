package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.ExamPaperService;
import com.example.appbackend.service.ExamPaperService.DownloadFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamPaperControllerTest {

    private static final String CREATE_REQUEST = """
            {"title":"期末考试","pageSize":"A4","orientation":"PORTRAIT","columnsCount":1,
             "selectionMode":"MANUAL","questions":[{"questionId":3,"score":5,"sortOrder":1}]}
            """;
    private static final String PREVIEW_REQUEST = """
            {"rules":[{"type":"single_choice","difficulty":"easy","quantity":1}]}
            """;

    private ExamPaperService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ExamPaperService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ExamPaperController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingUserIdReturnsUnauthorizedResult() throws Exception {
        mockMvc.perform(get("/api/exam/papers/7"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("请先登录"));
    }

    @Test
    void createDelegatesWithAuthenticatedUserId() throws Exception {
        when(service.create(any(CreateRequest.class), any())).thenReturn(new PaperVO());

        mockMvc.perform(post("/api/exam/papers")
                        .requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).create(any(CreateRequest.class), org.mockito.ArgumentMatchers.eq(42L));
    }

    @Test
    void downloadReturnsDocxHeadersAndSanitizedUtf8Filename() throws Exception {
        byte[] docx = "docx".getBytes(StandardCharsets.UTF_8);
        when(service.download(7L, 42L, DownloadContent.PAPER))
                .thenReturn(new DownloadFile("期末\r\n考试/卷", docx));

        mockMvc.perform(get("/api/exam/papers/7/download")
                        .requestAttr("userId", 42L)
                        .param("content", "PaPeR"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("UTF-8''")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.not(containsString("%0D"))))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.not(containsString("%0A"))))
                .andExpect(content().bytes(docx));
    }

    @Test
    void invalidDownloadContentReturnsBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/exam/papers/7/download")
                        .requestAttr("userId", 42L)
                        .param("content", "questions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(service);
    }

    @Test
    void missingDownloadContentReturnsBusinessBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/exam/papers/7/download")
                        .requestAttr("userId", 42L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("content 仅支持 paper 或 answer"));

        verifyNoInteractions(service);
    }

    @Test
    void downloadAuthenticatesBeforeValidatingContent() throws Exception {
        mockMvc.perform(get("/api/exam/papers/7/download")
                        .param("content", "questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(service);
    }

    @Test
    void randomPreviewAndCreateRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/exam/papers/random-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PREVIEW_REQUEST))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/exam/papers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void listRequiresAuthenticationAndDelegatesAuthenticatedUserId() throws Exception {
        mockMvc.perform(get("/api/exam/papers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/exam/papers")
                        .requestAttr("userId", 42L)
                        .param("current", "2")
                        .param("size", "20"))
                .andExpect(status().isOk());

        verify(service).list(2, 20, 42L);
    }

    @Test
    void detailDelegatesAuthenticatedUserId() throws Exception {
        mockMvc.perform(get("/api/exam/papers/7")
                        .requestAttr("userId", 42L))
                .andExpect(status().isOk());

        verify(service).detail(7L, 42L);
    }

    @Test
    void answerDownloadIsCaseInsensitive() throws Exception {
        when(service.download(7L, 42L, DownloadContent.ANSWER))
                .thenReturn(new DownloadFile("期末考试", new byte[]{1}));

        mockMvc.perform(get("/api/exam/papers/7/download")
                        .requestAttr("userId", 42L)
                        .param("content", "aNsWeR"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));

        verify(service).download(7L, 42L, DownloadContent.ANSWER);
    }
}
