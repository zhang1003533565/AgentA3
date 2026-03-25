package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/map/nearby")
@Tag(name = "周边设施", description = "周边设施查询和分类统计接口")
public class MapNearbyController {

    @Autowired
    private MapService mapService;

    @GetMapping
    @Operation(summary = "查询周边设施", description = "根据坐标查询周边设施，支持距离排序")
    public Result<NearbyResponse> getNearbyList(
            @Parameter(description = "当前经度", required = true)
            @RequestParam Double longitude,
            @Parameter(description = "当前纬度", required = true)
            @RequestParam Double latitude,
            @Parameter(description = "搜索半径（米），默认500")
            @RequestParam(required = false) Double radius,
            @Parameter(description = "设施类型筛选")
            @RequestParam(required = false) Integer facilityType,
            @Parameter(description = "返回数量，默认20")
            @RequestParam(defaultValue = "20") Integer limit,
            @Parameter(description = "排序方式：distance（默认）/ name")
            @RequestParam(defaultValue = "distance") String sortBy) {
        NearbyResponse result = mapService.getNearbyList(longitude, latitude, radius, facilityType, limit, sortBy);
        return Result.success(result);
    }

    @GetMapping("/count")
    @Operation(summary = "获取周边设施分类统计", description = "统计周边各类型设施数量")
    public Result<NearbyCountResponse> getNearbyCount(
            @Parameter(description = "当前经度", required = true)
            @RequestParam Double longitude,
            @Parameter(description = "当前纬度", required = true)
            @RequestParam Double latitude,
            @Parameter(description = "搜索半径（米），默认1000")
            @RequestParam(required = false) Double radius) {
        NearbyCountResponse result = mapService.getNearbyCount(longitude, latitude, radius);
        return Result.success(result);
    }
}
