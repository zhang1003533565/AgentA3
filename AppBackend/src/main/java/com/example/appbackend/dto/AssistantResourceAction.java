package com.example.appbackend.dto;

import lombok.Data;

@Data
public class AssistantResourceAction {
    private String type;
    private String label;
    private String target;
    private boolean requiresAuth;
}
