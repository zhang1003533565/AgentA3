package com.example.appbackend.service;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewFile;
import com.example.appbackend.dto.ExamPaperPreviewDTO.PreviewResponse;

public interface ExamPaperPreviewService {
    PreviewResponse createPreview(CreateRequest request, Long userId);
    PreviewFile getPreview(String token, Long userId);
    void deletePreview(String token, Long userId);
}
