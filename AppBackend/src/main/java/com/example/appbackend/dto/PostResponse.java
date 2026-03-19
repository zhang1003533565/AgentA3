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

    @Schema(description = "帖子ID", example = "1")
    private Long id;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "张三")
    private String username;

    @Schema(description = "用户头像", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "帖子标题", example = "考研经验分享")
    private String title;

    @Schema(description = "帖子内容", example = "分享一下我的考研经验...")
    private String content;

    @Schema(description = "图片URL列表", example = "[\"https://example.com/img1.jpg\"]")
    private String images;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;

    @Schema(description = "话题名称", example = "考研")
    private String topicName;

    @Schema(description = "浏览量", example = "100")
    private Integer viewCount;

    @Schema(description = "点赞数", example = "50")
    private Integer likeCount;

    @Schema(description = "评论数", example = "20")
    private Integer commentCount;

    @Schema(description = "是否点赞", example = "true")
    private Boolean isLiked;

    @Schema(description = "是否收藏", example = "false")
    private Boolean isFavorited;

    @Schema(description = "帖子状态", example = "PUBLISHED")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
