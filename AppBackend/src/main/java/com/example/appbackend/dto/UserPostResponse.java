package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPostResponse {
    Long id;
    String title;
    Integer viewCount;
    Integer likeCount;
    Integer commentCount;
    private LocalDateTime createTime;
}
