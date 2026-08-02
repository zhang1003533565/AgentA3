package com.example.appbackend.dto;

import com.example.appbackend.entity.MapFloorPlan;
import com.example.appbackend.entity.MapPlaceFence;
import com.example.appbackend.entity.MapPlaceImage;
import com.example.appbackend.entity.MapPlaceIndoorPosition;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MapPlaceResponse {
    private Long id;
    private Long parentId;
    private String sceneType;
    private String placeType;
    private String name;
    private String description;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MapPlaceImage> images = new ArrayList<>();
    private MapPlaceFence fence;
    private MapFloorPlan floorPlan;
    private MapPlaceIndoorPosition indoorPosition;
    private List<MapPlaceResponse> children = new ArrayList<>();
}
