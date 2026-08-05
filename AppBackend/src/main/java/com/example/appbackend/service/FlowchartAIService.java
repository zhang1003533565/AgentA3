package com.example.appbackend.service;

import com.example.appbackend.dto.FlowchartDTO;

public interface FlowchartAIService {
    FlowchartDTO.FlowchartData generate(FlowchartDTO.GenerateRequest request, String inputText, String authorization);
}
