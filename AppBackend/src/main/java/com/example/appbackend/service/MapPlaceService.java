package com.example.appbackend.service;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.MapFloorPlan;
import com.example.appbackend.entity.MapPlaceFence;
import com.example.appbackend.entity.MapPlaceImage;
import com.example.appbackend.entity.MapPlaceIndoorPosition;

import java.util.List;

public interface MapPlaceService {
    List<MapPlaceResponse> list(String sceneType, Long parentId, String placeType, String status, String keyword);
    List<MapPlaceResponse> tree(String sceneType);
    MapPlaceResponse detail(Long id);
    MapPlaceResponse create(MapPlaceRequest request);
    MapPlaceResponse update(Long id, MapPlaceRequest request);
    void delete(Long id);

    List<MapPlaceImage> listImages(Long placeId);
    MapPlaceImage addImage(Long placeId, MapPlaceImageRequest request);
    MapPlaceImage updateImage(Long imageId, MapPlaceImageRequest request);
    void deleteImage(Long imageId);

    MapPlaceFence getFence(Long placeId);
    MapPlaceFence saveFence(Long placeId, MapPlaceFenceRequest request);
    void deleteFence(Long placeId);

    MapFloorPlan getFloorPlan(Long floorPlaceId);
    MapFloorPlan saveFloorPlan(Long floorPlaceId, MapFloorPlanRequest request);
    void deleteFloorPlan(Long floorPlaceId);

    List<MapPlaceIndoorPosition> listPositions(Long floorPlanId);
    MapPlaceIndoorPosition savePosition(Long placeId, MapIndoorPositionRequest request);
    void deletePosition(Long positionId);
}
