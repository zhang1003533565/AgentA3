package com.example.appbackend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapIndoorPositionRequest {
    private Long floorPlanId;
    private BigDecimal xRatio;
    private BigDecimal yRatio;
}
