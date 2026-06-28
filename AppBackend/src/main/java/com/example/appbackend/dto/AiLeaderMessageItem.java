package com.example.appbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AiLeaderMessageItem {
    private Long id;
    private String role;
    private String content;
    private String answerType;
    private String outputType;
    private List<String> outputTypes;
    private Map<String, Object> outputMeta;
    private List<Map<String, Object>> attachments;
    private LocalDateTime createTime;
}
