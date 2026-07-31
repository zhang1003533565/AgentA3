package com.example.appbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DishCuisineRequest {

    @NotNull(message = "所属食堂点位 ID 不能为空")
    private Long canteenPlaceId;

    @NotBlank(message = "分类名称不能为空")
    private String name;

    private Integer status = 1;

    private Integer sortOrder = 0;
}
