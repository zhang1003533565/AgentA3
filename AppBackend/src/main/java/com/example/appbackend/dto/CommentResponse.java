package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评论详情响应")
public class CommentResponse {

    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String username;
    private String avatar;
    private Long parentId;
    private Long replyToId;
    private String replyToUsername;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Boolean isLiked;
    private String status;
    private List<CommentResponse> children;
    private LocalDateTime createTime;
}
