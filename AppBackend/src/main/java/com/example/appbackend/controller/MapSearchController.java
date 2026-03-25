package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/map")
@Tag(name = "地图搜索", description = "设施搜索、快速定位、按类型查询接口")
public class MapSearchController {

    @Autowired
    private MapService mapService;

    @GetMapping("/search")
    @Operation(summary = "搜索设施", description = "根据关键字搜索设施，返回匹配列表")
    public Result<List<MarkerSummaryItem>> searchFacilities(
            @Parameter(description = "搜索关键字", required = true)
            @RequestParam String keyword,
            @Parameter(description = "设施类型")
            @RequestParam(required = false) Integer facilityType,
            @Parameter(description = "返回数量限制，默认10")
            @RequestParam(defaultValue = "10") Integer limit) {
        List<MarkerSummaryItem> results = mapService.searchFacilities(keyword, facilityType, limit);
        return Result.success(results);
    }

    @GetMapping("/locate")
    @Operation(summary = "快速定位", description = "根据设施名称快速定位到地图位置")
    public Result<LocateResponse> locate(
            @Parameter(description = "设施名称或关键词", required = true)
            @RequestParam String keyword) {
        LocateResponse result = mapService.locate(keyword);
        return Result.success(result);
    }

    @GetMapping("/marker/by-type/{facilityType}")
    @Operation(summary = "按类型获取标记", description = "根据设施类型获取所有标记")
    public Result<List<MarkerResponse>> getMarkersByType(
            @Parameter(description = "设施类型：1-餐厅 2-运动场 3-教学楼 4-宿舍", required = true)
            @PathVariable Integer facilityType) {
        List<MarkerResponse> markers = mapService.getMarkersByType(facilityType);
        return Result.success(markers);
    }
}
