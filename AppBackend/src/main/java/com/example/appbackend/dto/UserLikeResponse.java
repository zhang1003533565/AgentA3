package com.example.appbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserLikeResponse {
    Long id;
    Long postId;
    String postTitle;
    private LocalDateTime createTime;
}
