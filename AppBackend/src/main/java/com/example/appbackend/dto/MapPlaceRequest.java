package com.example.appbackend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapPlaceRequest {
    private Long parentId;
    private String sceneType;
    private String placeType;
    private String name;
    private String description;
    private String usagePurpose;
    private String usageStatus;
    private String status;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String locationDesc;
    private Boolean mapVisible;
    private Integer sortOrder;
    private Integer stallStatus;
    private String businessHours;
    private BigDecimal avgPrice;
    private String imageUrl;
}
