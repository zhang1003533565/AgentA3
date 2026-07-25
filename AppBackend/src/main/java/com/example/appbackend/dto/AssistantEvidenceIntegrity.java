package com.example.appbackend.dto;

import lombok.Data;

@Data
public class AssistantEvidenceIntegrity {
    private String algorithm;
    private String digest;
    private String scope;
    private boolean signed;
}
