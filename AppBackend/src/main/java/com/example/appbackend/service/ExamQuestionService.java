package com.example.appbackend.service;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.PageResponse;

public interface ExamQuestionService {

    ExamQuestionDTO.ReviewResponse review(ExamQuestionDTO.ImportRequest request, String expectedType);

    ExamQuestionDTO.ImportResponse importQuestions(ExamQuestionDTO.ImportRequest request, String expectedType, Long userId);

    ExamQuestionDTO.ImportResponse importPublicQuestions(ExamQuestionDTO.ImportRequest request, String expectedType, Long adminUserId);

    PageResponse<ExamQuestionDTO.QuestionVO> listQuestions(Integer current, Integer size, String type, String difficulty, String keyword, Long userId);

    ExamQuestionDTO.QuestionVO getQuestion(Long id, Long userId);
}
