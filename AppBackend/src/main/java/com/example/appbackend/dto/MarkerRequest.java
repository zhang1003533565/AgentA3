package com.example.appbackend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "标记创建/更新请求")
public class MarkerRequest {

    @NotNull(message = "关联设施ID不能为空")
    @Schema(description = "关联设施ID")
    private Long facilityId;

    @Schema(description = "自定义图标URL")
    private String iconUrl;

    @Schema(description = "排序，默认0")
    private Integer sort;
}
