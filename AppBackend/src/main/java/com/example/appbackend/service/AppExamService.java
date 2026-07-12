package com.example.appbackend.service;

import com.example.appbackend.dto.AppExamDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

public interface AppExamService {
    Page<AppExamDTO.PaperSummary> listPublished(Long userId, int page, int size, String keyword);

    AppExamDTO.PaperDetail paperDetail(Long paperId, Long userId);

    AppExamDTO.AttemptDetail startOrResume(Long paperId, Long userId, LocalDateTime now);

    AppExamDTO.AttemptDetail attemptDetail(Long attemptId, Long userId, LocalDateTime now);
}
