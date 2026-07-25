package com.example.appbackend.dto;

import lombok.Data;

@Data
public class AssistantResourceIntegrity {
    private String algorithm;
    private String digest;
    private Long size;
}
