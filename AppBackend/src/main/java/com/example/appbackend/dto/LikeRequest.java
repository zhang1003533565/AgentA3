package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "点赞请求")
public class LikeRequest {

    @NotNull(message = "点赞目标ID不能为空")
    @Schema(description = "点赞目标ID", example = "1")
    private Long targetId;

    @NotBlank(message = "点赞目标类型不能为空")
    @Schema(description = "点赞目标类型：POST-帖子，COMMENT-评论", example = "POST")
    private String targetType;
}
