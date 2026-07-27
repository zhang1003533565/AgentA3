package com.example.appbackend.dto;

import lombok.Data;

@Data
public class MapPlaceFenceRequest {
    private String geometryType;
    private String geometryData;
}
