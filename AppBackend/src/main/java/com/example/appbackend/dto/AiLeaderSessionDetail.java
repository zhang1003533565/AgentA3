package com.example.appbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiLeaderSessionDetail {
    private AiLeaderSessionItem session;
    private List<AiLeaderMessageItem> messages;
}
