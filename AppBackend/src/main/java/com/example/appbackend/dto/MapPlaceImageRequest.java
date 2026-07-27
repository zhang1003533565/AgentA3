package com.example.appbackend.dto;

import lombok.Data;

@Data
public class MapPlaceImageRequest {
    private String imageUrl;
    private Integer sortOrder;
}
