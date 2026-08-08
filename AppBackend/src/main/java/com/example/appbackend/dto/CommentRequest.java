package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评论创建请求")
public class CommentRequest {

    @NotNull(message = "帖子ID不能为空")
    @Schema(description = "帖子ID", example = "1")
    private Long postId;

    @NotBlank(message = "评论内容不能为空")
    @Schema(description = "评论内容", example = "写得很好，学习了！")
    private String content;

    @Schema(description = "父评论ID(用于回复)", example = "1")
    private Long parentId;

    @Schema(description = "回复目标用户ID", example = "2")
    private Long replyToId;

    @Schema(description = "评论图片URL列表")
    private List<String> images;
}
