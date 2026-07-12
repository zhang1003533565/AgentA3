package com.example.appbackend.service;

import com.example.appbackend.dto.QuestionGenerationDTO.OptionsResponse;

public interface QuestionGenerationService {

    OptionsResponse getOptions(String authorization);
}
