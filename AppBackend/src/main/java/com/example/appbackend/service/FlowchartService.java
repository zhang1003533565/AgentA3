package com.example.appbackend.service;

import com.example.appbackend.dto.FlowchartDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FlowchartService {
    FlowchartDTO.GenerateResponse generate(Long userId, FlowchartDTO.GenerateRequest request, String authorization);

    FlowchartDTO.UploadResponse uploadAndParse(Long userId, MultipartFile file);

    List<FlowchartDTO.HistoryItem> history(Long userId);

    FlowchartDTO.GenerateResponse detail(Long userId, String id);

    void delete(Long userId, String id);
}
