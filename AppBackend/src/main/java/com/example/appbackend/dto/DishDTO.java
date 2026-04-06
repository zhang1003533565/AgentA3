package com.example.appbackend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class DishDTO {

    private Long id;

    @NotBlank(message = "菜品名称不能为空")
    private String name;

    @NotNull(message = "所属档口 ID 不能为空")
    private Long stallId;

    @NotNull(message = "菜品价格不能为空")
    private BigDecimal price;

    private String category;

    private String imageUrl;

    private BigDecimal rating;

    private Integer soldCount;

    private Boolean isAvailable;

    private String taste;

    private String description;

    private String stallName;
}