package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "论坛消息未读数聚合响应")
public class ForumMessageUnreadResponse {

    @Schema(description = "收到的评论数（他人评论了我的帖子）")
    private Long commentCount;

    @Schema(description = "被点赞的帖子数")
    private Long likeCount;

    @Schema(description = "系统通知数（公告话题帖数）")
    private Long systemCount;
}
