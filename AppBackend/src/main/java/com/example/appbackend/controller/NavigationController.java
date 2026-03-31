package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.NavigationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/map/navigation")
@Tag(name = "路线导航", description = "发起导航、获取路线、到达确认、历史记录接口")
public class NavigationController {

    @Autowired
    private NavigationService navigationService;

    @PostMapping
    @Operation(summary = "发起导航", description = "创建导航记录并返回路线信息")
    public Result<NavigationResponse> startNavigation(
            @Valid @RequestBody NavigationRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        NavigationResponse result = navigationService.startNavigation(request, userId);
        return Result.success(result);
    }

    @GetMapping("/route")
    @Operation(summary = "获取导航路线", description = "获取起点到终点的路线（不创建导航记录）。国内坐标：经度约 73–135，纬度约 18–53，勿将二者填反。")
    public Result<NavigationRouteResponse> getRoute(
            @Parameter(description = "起点经度（如北京约 116.x）", required = true)
            @RequestParam BigDecimal fromLongitude,
            @Parameter(description = "起点纬度（如北京约 39.x）", required = true)
            @RequestParam BigDecimal fromLatitude,
            @Parameter(description = "终点经度", required = true)
            @RequestParam BigDecimal toLongitude,
            @Parameter(description = "终点纬度", required = true)
            @RequestParam BigDecimal toLatitude,
            @Parameter(description = "出行方式：walking（步行，默认）/ driving（驾车）/ bicycling（骑行）")
            @RequestParam(required = false) String mode) {
        NavigationRouteResponse result = navigationService.getRoute(
                fromLongitude, fromLatitude, toLongitude, toLatitude, mode);
        return Result.success(result);
    }

    @PostMapping("/{navigationId}/arrive")
    @Operation(summary = "到达确认", description = "确认已到达导航目的地")
    public Result<Void> arriveConfirm(
            @PathVariable Long navigationId,
            HttpServletRequest httpRequest) {
        navigationService.arriveConfirm(navigationId);
        return Result.success("到达确认成功", null);
    }

    @PostMapping("/{navigationId}/cancel")
    @Operation(summary = "取消导航", description = "取消当前导航")
    public Result<Void> cancelNavigation(
            @PathVariable Long navigationId,
            HttpServletRequest httpRequest) {
        navigationService.cancelNavigation(navigationId);
        return Result.success("导航已取消", null);
    }

    @GetMapping("/history")
    @Operation(summary = "获取导航历史", description = "获取用户导航历史记录")
    public Result<PageResponse<NavigationHistoryItem>> getNavigationHistory(
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认10")
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        PageResponse<NavigationHistoryItem> result = navigationService.getNavigationHistory(userId, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/reverse-geocode")
    @Operation(summary = "逆地址解析", description = "根据坐标查询对应地址信息（坐标 → 地址）")
    public Result<ReverseGeocoderResponse> reverseGeocode(
            @Parameter(description = "经度", required = true)
            @RequestParam BigDecimal longitude,
            @Parameter(description = "纬度", required = true)
            @RequestParam BigDecimal latitude) {
        ReverseGeocoderResponse result = navigationService.reverseGeocode(longitude, latitude);
        return Result.success(result);
    }

    @GetMapping("/geocode")
    @Operation(summary = "地址解析", description = "根据地址查询对应坐标（地址 → 坐标）。纯学校/POI 名称建议传 region（如「张家口」「河北」）；服务端在地理编码 348 时会自动尝试关键词建议接口。")
    public Result<GeocoderResponse> geocode(
            @Parameter(description = "地址字符串，如\"食堂\"或\"北京市朝阳区阜通东大街6号\"", required = true)
            @RequestParam String address,
            @Parameter(description = "限定城市/区域（可选），与腾讯 region 一致，可提高「河北建筑工程学院」等 POI 解析成功率")
            @RequestParam(required = false) String region) {
        GeocoderResponse result = navigationService.geocode(address, region);
        return Result.success(result);
    }

    @GetMapping("/places/search")
    @Operation(summary = "关键词 POI 搜索", description = "搜索附近的地点（关键词建议）")
    public Result<PlaceSearchResponse> searchPlaces(
            @Parameter(description = "搜索关键词", required = true)
            @RequestParam String keyword,
            @Parameter(description = "限定城市/区域名称（可选）")
            @RequestParam(required = false) String region,
            @Parameter(description = "中心点纬度（可选，用于距离排序）")
            @RequestParam(required = false) BigDecimal latitude,
            @Parameter(description = "中心点经度（可选，用于距离排序）")
            @RequestParam(required = false) BigDecimal longitude,
            @Parameter(description = "搜索半径（米，仅在指定中心点时有效，默认1000）")
            @RequestParam(required = false, defaultValue = "1000") Integer radius) {
        PlaceSearchResponse result = navigationService.searchPlaces(keyword, region, latitude, longitude, radius);
        return Result.success(result);
    }

    @PostMapping("/coords/translate")
    @Operation(summary = "坐标批量转换", description = "将坐标从其他坐标系批量转换为 GCJ-02（腾讯/高德标准）")
    public Result<CoordTranslateResponse> translateCoords(
            @Valid @RequestBody CoordTranslateRequest request,
            @Parameter(description = "来源坐标系：1=GPS(WGS-84) 2=sogou 3=百度(BD-09) 4=GCJ-02（默认）")
            @RequestParam(required = false, defaultValue = "4") Integer fromCoordSys) {
        CoordTranslateResponse result = navigationService.translateCoords(request.getPoints(), fromCoordSys);
        return Result.success(result);
    }
}
