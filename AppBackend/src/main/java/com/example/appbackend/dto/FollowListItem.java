package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "关注/粉丝列表项")
public class FollowListItem {

    @Schema(description = "关注记录ID", example = "1")
    private Long id;

    @Schema(description = "用户ID（粉丝列表时为粉丝ID，关注列表时为被关注用户ID）", example = "2")
    private Long userId;

    @Schema(description = "被关注用户ID（关注列表时使用）", example = "3")
    private Long followId;

    @Schema(description = "用户名", example = "张三")
    private String username;

    @Schema(description = "用户头像", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "当前登录用户是否关注了该用户", example = "true")
    private Boolean isFollowing;

    @Schema(description = "关注时间", example = "2026-03-17T10:00:00")
    private LocalDateTime createTime;
}
