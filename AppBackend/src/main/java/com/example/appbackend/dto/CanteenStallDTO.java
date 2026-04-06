package com.example.appbackend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class CanteenStallDTO {

    private Long id;

    @NotBlank(message = "档口名称不能为空")
    private String stallName;

    @NotNull(message = "所属餐厅 ID 不能为空")
    private Long restaurantId;

    private String floor;

    private String category;

    private String location;

    private BigDecimal score;

    private Integer reviewCount;

    private Integer recommendRate;

    private BigDecimal avgPrice;

    private String businessHours;

    private String image;

    private String description;

    private Integer status;

    private Integer sort;

    private String restaurantName;
}