package com.example.appbackend.service;

import com.example.appbackend.dto.ExamPaperDTO.CreateRequest;
import com.example.appbackend.dto.ExamPaperDTO.DownloadContent;
import com.example.appbackend.dto.ExamPaperDTO.PaperVO;
import com.example.appbackend.dto.ExamPaperDTO.RandomPreviewRequest;
import com.example.appbackend.dto.PageResponse;

public interface ExamPaperService {

    PaperVO randomPreview(RandomPreviewRequest request);

    PaperVO create(CreateRequest request, Long userId);

    PageResponse<PaperVO> list(Integer current, Integer size, String keyword, Long userId);

    PaperVO detail(Long id, Long userId);

    DownloadFile download(Long id, Long userId, DownloadContent content);

    record DownloadFile(String title, byte[] bytes) {
    }
}
