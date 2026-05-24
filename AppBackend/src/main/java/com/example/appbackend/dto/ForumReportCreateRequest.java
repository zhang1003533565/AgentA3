package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "论坛举报创建请求")
public class ForumReportCreateRequest {

    @NotNull(message = "举报目标类型不能为空")
    @Schema(description = "举报目标类型：1-帖子，2-评论", example = "1")
    private Integer targetType;

    @NotNull(message = "举报目标ID不能为空")
    @Schema(description = "举报目标ID", example = "1")
    private Long targetId;

    @Schema(description = "举报原因类型", example = "1")
    private Integer reasonType;

    @Size(max = 100, message = "举报原因不能超过100个字符")
    @Schema(description = "举报原因文本")
    private String reasonText;

    @Size(max = 1000, message = "举报描述不能超过1000个字符")
    @Schema(description = "举报补充描述")
    private String description;
}
