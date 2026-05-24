package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "帖子详情响应")
public class PostResponse {

    private Long id;
    private Long userId;
    private String username;
    private String avatar;
    private String title;
    private String content;
    private String images;
    private Long topicId;
    private String topicName;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isLiked;
    private Boolean isFavorited;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
