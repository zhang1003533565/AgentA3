package com.example.appbackend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapIndoorPositionResponse {
    private Long id;
    private Long placeId;
    private Long floorPlanId;
    private BigDecimal xRatio;
    private BigDecimal yRatio;
    private String name;
    private String placeType;
    private String description;
    private String status;
    private String locationDesc;
    private Integer stallStatus;
    private String businessHours;
    private BigDecimal avgPrice;
    private String imageUrl;
    private Long floorPlaceId;
    private String floorName;
}
