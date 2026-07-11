package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.service.ExamPaperService;
import com.example.appbackend.service.ExamPaperService.DownloadFile;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExamPaperControllerTest {

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
        String request = """
                {"title":"期末考试","pageSize":"A4","orientation":"PORTRAIT","columnsCount":1,
                 "selectionMode":"MANUAL","questions":[{"questionId":3,"score":5,"sortOrder":1}]}
                """;

        mockMvc.perform(post("/api/exam/papers")
                        .requestAttr("userId", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
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
    }

    @Test
    void downloadAuthenticatesBeforeValidatingContent() throws Exception {
        mockMvc.perform(get("/api/exam/papers/7/download")
                        .param("content", "questions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
