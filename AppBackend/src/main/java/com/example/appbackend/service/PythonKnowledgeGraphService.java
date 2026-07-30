package com.example.appbackend.service;

import com.example.appbackend.dto.KnowledgeGraphDTO;

public interface PythonKnowledgeGraphService {

    KnowledgeGraphDTO.GraphView getGraph(Long userId);
}
