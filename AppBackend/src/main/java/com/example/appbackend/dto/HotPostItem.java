package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "热门帖子响应")
public class HotPostItem {

    @Schema(description = "帖子ID", example = "1")
    private Long id;

    @Schema(description = "帖子标题", example = "热门帖子标题")
    private String title;

    @Schema(description = "浏览量", example = "1000")
    private Integer viewCount;

    @Schema(description = "点赞数", example = "500")
    private Integer likeCount;

    @Schema(description = "评论数", example = "100")
    private Integer commentCount;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "张三")
    private String username;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
