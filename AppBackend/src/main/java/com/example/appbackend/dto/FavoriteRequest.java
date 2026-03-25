package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "添加收藏请求")
public class FavoriteRequest {

    @NotNull(message = "标记ID不能为空")
    @Schema(description = "标记ID")
    private Long markerId;

    @Schema(description = "备注名称")
    private String remark;
}
