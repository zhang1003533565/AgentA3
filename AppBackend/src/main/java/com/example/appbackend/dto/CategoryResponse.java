package com.example.appbackend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "分类响应")
public class CategoryResponse {

    @Schema(description = "分类ID")
    private Long id;

    @JsonProperty("name")  // 前端表格使用 name 字段
    @Schema(description = "分类名称", example = "学术活动")
    private String categoryName;

    @Schema(description = "排序", example = "1")
    private Integer sort;

    @Schema(description = "状态 1-启用 0-禁用", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
