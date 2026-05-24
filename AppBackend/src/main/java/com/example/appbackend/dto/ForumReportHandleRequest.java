package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "论坛举报处理请求")
public class ForumReportHandleRequest {

    @NotBlank(message = "处理动作不能为空")
    @Schema(description = "处理动作：IGNORE-忽略举报，DELETE_CONTENT-删除被举报帖子/评论", example = "DELETE_CONTENT")
    private String action;

    @Size(max = 500, message = "处理结果不能超过500字")
    @Schema(description = "处理说明", example = "内容包含违规信息，已删除")
    private String handleResult;
}
