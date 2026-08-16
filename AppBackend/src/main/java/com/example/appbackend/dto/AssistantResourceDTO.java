package com.example.appbackend.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AssistantResourceDTO {
    private String schemaVersion;
    private String id;
    private Long messageId;
    private String kind;
    private String deliveryType;
    private String groundingStatus;
    private String title;
    private String summary;
    private String mimeType;
    private String storageKey;
    private String url;
    private String previewUrl;
    private String sourceType;
    private String sourceId;
    private List<String> evidenceIds;
    private List<AssistantResourceAction> actions;
    private String authScope;
    private String createdAt;
    private String expiresAt;
    private AssistantResourceIntegrity integrity;
    private Map<String, Object> payload;
    private Map<String, Object> metadata;
    private String availability;
}
