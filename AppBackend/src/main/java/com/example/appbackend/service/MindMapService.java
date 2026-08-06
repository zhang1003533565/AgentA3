package com.example.appbackend.service;

import com.example.appbackend.dto.MindMapDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MindMapService {
    MindMapDTO.GenerateResponse generate(Long userId, MindMapDTO.GenerateRequest request, String authorization);

    MindMapDTO.GenerateResponse optimize(Long userId, MindMapDTO.OptimizeRequest request, String authorization);

    MindMapDTO.UploadResponse uploadAndParse(Long userId, MultipartFile file);

    List<MindMapDTO.HistoryItem> history(Long userId);

    MindMapDTO.GenerateResponse detail(Long userId, String id);
}
