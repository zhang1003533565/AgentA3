package com.example.appbackend.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AssistantEvidenceSource {
    private String evidenceId;
    private String sourceType;
    private String sourceId;
    private String title;
    private String excerpt;
    private String sourceVersion;
    private String retrievedAt;
    private String contentDigest;
    private String accessScope;
    private Map<String, Object> metadata;
}
