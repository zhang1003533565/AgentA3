package com.example.appbackend.dto;

import lombok.Data;

@Data
public class DishReviewSummaryDTO {

    private Integer totalCount;

    private Integer recommendCount;

    private Integer neutralCount;

    private Integer avoidCount;

    private Integer recommendRate;
}
