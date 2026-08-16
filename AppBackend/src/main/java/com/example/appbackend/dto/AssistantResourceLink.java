package com.example.appbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssistantResourceLink {
    private String resourceId;
    private List<String> evidenceIds;
}
