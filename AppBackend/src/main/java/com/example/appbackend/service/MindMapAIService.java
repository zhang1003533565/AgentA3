package com.example.appbackend.service;

import com.example.appbackend.dto.MindMapDTO;

public interface MindMapAIService {
    MindMapDTO.MindMapData generate(String inputText,
                                    String centerTopic,
                                    String centerTopicMode,
                                    String depth,
                                    String structure,
                                    String detail,
                                    String authorization);

    MindMapDTO.MindMapData optimize(MindMapDTO.MindMapData currentMindMap, String userInstruction, String authorization);
}
