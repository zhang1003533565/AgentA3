package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/map/marker")
@Tag(name = "地图标记", description = "地图标记的增删改查接口")
public class MapMarkerController {

    @Autowired
    private MapService mapService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }

    @GetMapping("/list")
    @Operation(summary = "获取标记列表", description = "分页查询地图标记列表，支持类型、关键字筛选")
    public Result<PageResponse<MarkerResponse>> getMarkerList(
            @Parameter(description = "设施类型：1-餐厅 2-运动场 3-教学楼 4-宿舍")
            @RequestParam(required = false) Integer facilityType,
            @Parameter(description = "搜索关键字")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认100")
            @RequestParam(defaultValue = "100") Integer pageSize) {
        PageResponse<MarkerResponse> result = mapService.getMarkerList(facilityType, keyword, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取标记详情", description = "根据ID获取地图标记详细信息")
    public Result<MarkerResponse> getMarkerDetail(@PathVariable Long id) {
        MarkerResponse marker = mapService.getMarkerDetail(id);
        return Result.success(marker);
    }

    @PostMapping
    @Operation(summary = "新增标记", description = "创建新地图标记（仅管理员）")
    public Result<MarkerResponse> createMarker(
            @Valid @RequestBody MarkerRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        MarkerResponse marker = mapService.createMarker(request);
        return Result.success("标记创建成功", marker);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新标记", description = "更新指定ID的地图标记（仅管理员）")
    public Result<MarkerResponse> updateMarker(
            @PathVariable Long id,
            @Valid @RequestBody MarkerRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        MarkerResponse marker = mapService.updateMarker(id, request);
        return Result.success("标记更新成功", marker);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标记", description = "删除指定ID的地图标记（仅管理员，逻辑删除）")
    public Result<Void> deleteMarker(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        mapService.deleteMarker(id);
        return Result.success("标记删除成功", null);
    }

    @PostMapping("/batch")
    @Operation(summary = "批量创建标记", description = "根据设施ID列表批量创建地图标记（仅管理员）")
    public Result<List<MarkerResponse>> batchCreateMarker(
            @RequestBody MarkerBatchRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        List<MarkerResponse> markers = mapService.batchCreateMarker(request.getFacilityIds());
        return Result.success("批量标记创建成功", markers);
    }

    @GetMapping("/icons")
    @Operation(summary = "获取标记图标列表", description = "获取所有设施类型的图标配置信息")
    public Result<List<MarkerIconInfo>> getMarkerIcons() {
        List<MarkerIconInfo> icons = mapService.getMarkerIcons();
        return Result.success(icons);
    }

    @PostMapping("/building")
    @Operation(summary = "管理员地图点击标点", description = "管理员点击地图同时创建设施和地图标记")
    public Result<MarkerResponse> createBuildingMarker(
            @Valid @RequestBody BuildingMarkerRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        MarkerResponse marker = mapService.createBuildingMarker(request);
        return Result.success("建筑标注创建成功", marker);
    }
}
