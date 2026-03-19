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

    @Schema(description = "评论ID", example = "1")
    private Long id;

    @Schema(description = "帖子ID", example = "1")
    private Long postId;

    @Schema(description = "评论者ID", example = "1")
    private Long userId;

    @Schema(description = "评论者用户名", example = "张三")
    private String username;

    @Schema(description = "评论者头像", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "父评论ID", example = "1")
    private Long parentId;

    @Schema(description = "回复目标用户ID", example = "2")
    private Long replyToId;

    @Schema(description = "回复目标用户名", example = "李四")
    private String replyToUsername;

    @Schema(description = "评论内容", example = "写得很好，学习了！")
    private String content;

    @Schema(description = "点赞数", example = "10")
    private Integer likeCount;

    @Schema(description = "是否点赞", example = "true")
    private Boolean isLiked;

    @Schema(description = "子评论列表")
    private List<CommentResponse> children;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
