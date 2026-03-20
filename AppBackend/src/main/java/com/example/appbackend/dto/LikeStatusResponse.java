package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "点赞状态响应")
public class LikeStatusResponse {

    @Schema(description = "是否点赞", example = "true")
    private Boolean liked;

    @Schema(description = "点赞数", example = "50")
    private Long likeCount;
}
