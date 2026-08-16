package com.example.appbackend.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AssistantEvidenceStep {
    private String stage;
    private Map<String, Object> detail;
}
