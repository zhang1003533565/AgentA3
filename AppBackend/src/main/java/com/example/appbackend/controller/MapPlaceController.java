package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.*;
import com.example.appbackend.service.MapPlaceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/map-places")
public class MapPlaceController {

    private final MapPlaceService mapPlaceService;

    public MapPlaceController(MapPlaceService mapPlaceService) {
        this.mapPlaceService = mapPlaceService;
    }

    @GetMapping
    public Result<List<MapPlaceResponse>> list(
            @RequestParam(required = false) String sceneType,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) String placeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return Result.success(mapPlaceService.list(sceneType, parentId, placeType, status, keyword));
    }

    @GetMapping("/tree")
    public Result<List<MapPlaceResponse>> tree(@RequestParam String sceneType) {
        return Result.success(mapPlaceService.tree(sceneType));
    }

    @GetMapping("/{id}")
    public Result<MapPlaceResponse> detail(@PathVariable Long id) {
        return Result.success(mapPlaceService.detail(id));
    }

    @PostMapping
    public Result<MapPlaceResponse> create(@RequestBody MapPlaceRequest request) {
        return Result.success("点位创建成功", mapPlaceService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MapPlaceResponse> update(@PathVariable Long id, @RequestBody MapPlaceRequest request) {
        return Result.success("点位更新成功", mapPlaceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapPlaceService.delete(id);
        return Result.success("点位删除成功", null);
    }

    @GetMapping("/{placeId}/images")
    public Result<List<MapPlaceImage>> images(@PathVariable Long placeId) {
        return Result.success(mapPlaceService.listImages(placeId));
    }

    @PostMapping("/{placeId}/images")
    public Result<MapPlaceImage> addImage(
            @PathVariable Long placeId,
            @RequestBody MapPlaceImageRequest request
    ) {
        return Result.success("图片添加成功", mapPlaceService.addImage(placeId, request));
    }

    @PutMapping("/images/{imageId}")
    public Result<MapPlaceImage> updateImage(
            @PathVariable Long imageId,
            @RequestBody MapPlaceImageRequest request
    ) {
        return Result.success("图片展示位置更新成功", mapPlaceService.updateImage(imageId, request));
    }

    @DeleteMapping("/images/{imageId}")
    public Result<Void> deleteImage(@PathVariable Long imageId) {
        mapPlaceService.deleteImage(imageId);
        return Result.success("图片删除成功", null);
    }

    @GetMapping("/{placeId}/fence")
    public Result<MapPlaceFence> fence(@PathVariable Long placeId) {
        return Result.success(mapPlaceService.getFence(placeId));
    }

    @PutMapping("/{placeId}/fence")
    public Result<MapPlaceFence> saveFence(
            @PathVariable Long placeId,
            @RequestBody MapPlaceFenceRequest request
    ) {
        return Result.success("围栏保存成功", mapPlaceService.saveFence(placeId, request));
    }

    @DeleteMapping("/{placeId}/fence")
    public Result<Void> deleteFence(@PathVariable Long placeId) {
        mapPlaceService.deleteFence(placeId);
        return Result.success("围栏删除成功", null);
    }

    @GetMapping("/floors/{floorPlaceId}/plan")
    public Result<MapFloorPlan> floorPlan(@PathVariable Long floorPlaceId) {
        return Result.success(mapPlaceService.getFloorPlan(floorPlaceId));
    }

    @PutMapping("/floors/{floorPlaceId}/plan")
    public Result<MapFloorPlan> saveFloorPlan(
            @PathVariable Long floorPlaceId,
            @RequestBody MapFloorPlanRequest request
    ) {
        return Result.success("平面图保存成功", mapPlaceService.saveFloorPlan(floorPlaceId, request));
    }

    @DeleteMapping("/floors/{floorPlaceId}/plan")
    public Result<Void> deleteFloorPlan(@PathVariable Long floorPlaceId) {
        mapPlaceService.deleteFloorPlan(floorPlaceId);
        return Result.success("平面图删除成功", null);
    }

    @GetMapping("/floor-plans/{floorPlanId}/positions")
    public Result<List<MapPlaceIndoorPosition>> positions(@PathVariable Long floorPlanId) {
        return Result.success(mapPlaceService.listPositions(floorPlanId));
    }

    @PutMapping("/{placeId}/indoor-position")
    public Result<MapPlaceIndoorPosition> savePosition(
            @PathVariable Long placeId,
            @RequestBody MapIndoorPositionRequest request
    ) {
        return Result.success("室内位置保存成功", mapPlaceService.savePosition(placeId, request));
    }

    @DeleteMapping("/indoor-positions/{positionId}")
    public Result<Void> deletePosition(@PathVariable Long positionId) {
        mapPlaceService.deletePosition(positionId);
        return Result.success("室内位置删除成功", null);
    }
}
