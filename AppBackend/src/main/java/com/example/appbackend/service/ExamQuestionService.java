package com.example.appbackend.service;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.PageResponse;

import java.util.List;

public interface ExamQuestionService {

    ExamQuestionDTO.ReviewResponse review(ExamQuestionDTO.ImportRequest request, String expectedType);

    ExamQuestionDTO.ImportResponse importQuestions(ExamQuestionDTO.ImportRequest request, String expectedType, Long userId);

    ExamQuestionDTO.ImportResponse importPublicQuestions(ExamQuestionDTO.ImportRequest request, String expectedType, Long adminUserId);

    PageResponse<ExamQuestionDTO.QuestionVO> listQuestions(Integer current, Integer size, String type, String difficulty, String keyword, String bankId, Long userId);

    ExamQuestionDTO.QuestionVO getQuestion(Long id, Long userId);

    ExamQuestionDTO.QuestionVO createQuestion(ExamQuestionDTO.SaveRequest request, Long userId, boolean admin);

    ExamQuestionDTO.QuestionVO updateQuestion(Long id, ExamQuestionDTO.SaveRequest request, Long userId, boolean admin);

    void deleteQuestion(Long id, Long userId, boolean admin);

    List<String> listBanks(Long userId);
}
