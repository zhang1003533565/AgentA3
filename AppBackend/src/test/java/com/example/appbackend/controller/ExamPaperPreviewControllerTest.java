package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewFile;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewResponse;
import com.example.appbackend.service.ExamPaperPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import com.example.appbackend.exception.GlobalExceptionHandler;
import com.example.appbackend.exception.BusinessException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExamPaperPreviewControllerTest {
    @Test
    void pdfIsPrivateNoStoreAndUsesAuthenticatedCreator() {
        ExamPaperPreviewService service = mock(ExamPaperPreviewService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(7L);
        when(service.getPreview("token", 7L)).thenReturn(new PreviewFile("x.pdf", "%PDF-x".getBytes(), 1));
        var response = new ExamPaperPreviewController(service).get("token", request);
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("no-store, private", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertArrayEquals("%PDF-x".getBytes(), response.getBody());
    }

    @Test
    void createAndDeleteDelegateWithAuthenticatedUser() {
        ExamPaperPreviewService service = mock(ExamPaperPreviewService.class);
        HttpServletRequest request = mock(HttpServletRequest.class); when(request.getAttribute("userId")).thenReturn(9L);
        CreateRequest create = new CreateRequest();
        when(service.createPreview(create, 9L)).thenReturn(new PreviewResponse("t", "/api/exam/papers/preview/t", Instant.now(), "c", "q", 2));
        assertEquals("t", new ExamPaperPreviewController(service).create(create, request).getData().getToken());
        new ExamPaperPreviewController(service).delete("t", request);
        verify(service).deletePreview("t", 9L);
    }

    @Test
    void lifecycleErrorsKeepHttpAuthenticationAndAuthorizationStatuses() throws Exception {
        ExamPaperPreviewService service = mock(ExamPaperPreviewService.class);
        MockMvc mvc = standaloneSetup(new ExamPaperPreviewController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/exam/papers/preview/token")).andExpect(status().isUnauthorized());
        doThrow(new BusinessException(403, "无权访问")).when(service).getPreview("token", 7L);
        mvc.perform(get("/api/exam/papers/preview/token").requestAttr("userId", 7L))
                .andExpect(status().isForbidden());
    }
}
