package com.example.appbackend.service;

import com.example.appbackend.dto.LearningKnowledgeDTO;

public interface CourseKnowledgeService {

    LearningKnowledgeDTO.RetrieveResponse retrieve(LearningKnowledgeDTO.RetrieveRequest request);
}
