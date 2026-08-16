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
    private String agentName;
    private String searchKeyword;
    private List<String> outputTypes;
    private Map<String, Object> outputMeta;
    private Map<String, Object> retrievalMeta;
    private List<Map<String, Object>> trace;
    private List<Map<String, Object>> attachments;
    private List<Map<String, Object>> matchedResults;
    private List<AssistantResourceDTO> resources;
    private AssistantEvidenceChainDTO evidenceChain;
    private LocalDateTime createTime;
}
