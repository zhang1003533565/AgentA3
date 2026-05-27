package com.example.appbackend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiLeaderMessageItem {
    private Long id;
    private String role;
    private String content;
    private String answerType;
    private LocalDateTime createTime;
}
