package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "收到的评论响应（他人评论了我的帖子）")
public class ReceivedCommentResponse {

    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String username;
    private String avatar;
    private String content;
    private LocalDateTime createTime;
}
