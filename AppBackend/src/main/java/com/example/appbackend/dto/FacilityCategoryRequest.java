package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FacilityCategoryRequest {

    @NotNull(message = "所属食堂 ID 不能为空")
    private Long restaurantId;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Integer status = 1;

    private Integer sortOrder = 0;
}
