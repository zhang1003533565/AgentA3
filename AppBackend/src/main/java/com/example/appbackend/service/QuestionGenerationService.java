package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionGenerationDTO.GenerationResponse;
import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionGenerationService {

    record GenerationCommand(
            String sourceType,
            MultipartFile file,
            String text,
            String questionType,
            Integer maxQuestions,
            String difficulty,
            String sourceTitle) {
    }

    OptionsResponse getOptions(String authorization);

    GenerationResponse generate(GenerationCommand command, String authorization);
}
