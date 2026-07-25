package com.example.appbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssistantEvidenceChainDTO {
    private String schemaVersion;
    private String chainId;
    private String requestId;
    private String status;
    private String generatedAt;
    private String evidenceState;
    private String queryDigest;
    private String answerDigest;
    private List<AssistantEvidenceSource> sources;
    private List<AssistantEvidenceStep> steps;
    private List<AssistantResourceLink> resourceLinks;
    private AssistantEvidenceGeneration generation;
    private AssistantEvidenceIntegrity integrity;
    private boolean truncated;
}
