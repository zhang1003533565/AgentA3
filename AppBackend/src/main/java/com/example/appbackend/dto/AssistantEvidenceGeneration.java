package com.example.appbackend.dto;

import lombok.Data;

@Data
public class AssistantEvidenceGeneration {
    private String agent;
    private String model;
    private String answerType;
    private boolean profileContextUsed;
}
