package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "帖子创建/更新请求")
public class PostRequest {

    @NotBlank(message = "帖子标题不能为空")
    @Size(max = 200, message = "帖子标题最多200字符")
    @Schema(description = "帖子标题", example = "考研经验分享")
    private String title;

    @NotBlank(message = "帖子内容不能为空")
    @Schema(description = "帖子内容", example = "分享一下我的考研经验...")
    private String content;

    @Schema(description = "图片URL列表(JSON数组格式)", example = "[\"https://example.com/img1.jpg\"]")
    private String images;

    @Schema(description = "话题ID", example = "1")
    private Long topicId;
}
