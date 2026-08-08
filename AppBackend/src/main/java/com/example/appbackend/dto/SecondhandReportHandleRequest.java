package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "二手物品举报处理请求")
public class SecondhandReportHandleRequest {

    @NotBlank(message = "处理动作不能为空")
    @Schema(description = "处理动作：IGNORE/OFFLINE_ITEM", requiredMode = Schema.RequiredMode.REQUIRED, example = "OFFLINE_ITEM")
    private String action;

    @NotBlank(message = "处理说明不能为空")
    @Size(max = 500, message = "处理说明不能超过500个字符")
    @Schema(description = "处理说明", requiredMode = Schema.RequiredMode.REQUIRED, example = "举报成立，已下架商品")
    private String handleResult;
}
