package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "分类请求")
public class CategoryRequest {
    @NotBlank(message = "分类名称不能为空")
    @JsonProperty("name")  // 前端提交的字段为 name，映射到 categoryName
    @Schema(description = "分类名称", example = "学术活动")
    private String categoryName;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态 1-启用 0-禁用", example = "1")
    private Integer status;
}
