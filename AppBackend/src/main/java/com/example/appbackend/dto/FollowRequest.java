package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "关注请求")
public class FollowRequest {

    @NotNull(message = "被关注用户ID不能为空")
    @Schema(description = "被关注用户ID", example = "1")
    private Long followId;
}
