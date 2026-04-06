package com.example.appbackend.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class DishReviewDTO {

    private Long id;

    @NotNull(message = "菜品 ID 不能为空")
    private Long dishId;

    private Long userId;

    private Long stallId;

    @NotNull(message = "评分不能为空")
    private BigDecimal rating;

    private String content;

    private String images;

    private Boolean isAnonymous;

    private Integer helpfulCount;

    private Integer replyCount;

    private Integer status;

    // 扩展字段
    private String dishName;

    private String stallName;

    private String userName;

    private String userAvatar;

    private String createTime;
}