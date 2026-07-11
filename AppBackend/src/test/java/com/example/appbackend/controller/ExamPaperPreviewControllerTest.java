package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewFile;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewResponse;
import com.example.appbackend.service.ExamPaperPreviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

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
}
