package com.example.appbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiLeaderSessionItem {
    private String sessionId;
    private String title;
    private String lastMessage;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
