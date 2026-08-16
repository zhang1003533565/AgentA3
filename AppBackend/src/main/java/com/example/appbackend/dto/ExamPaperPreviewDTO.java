package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public final class ExamPaperPreviewDTO {
    private ExamPaperPreviewDTO() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewResponse {
        private String token;
        private String pdfUrl;
        private Instant expiresAt;
        private String configurationHash;
        private String questionHash;
        private int pageCount;
    }

    public record PreviewFile(String filename, byte[] bytes, int pageCount) {}
}
