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

    private Long stallId;

    private Long stallPlaceId;

    @NotNull(message = "菜品价格不能为空")
    private BigDecimal price;

    private String category;

    private Long cuisineId;

    private String imageUrl;

    private BigDecimal rating;

    private Integer soldCount;

    private Boolean isAvailable;

    private String taste;

    private String description;

    private String stallName;
}
