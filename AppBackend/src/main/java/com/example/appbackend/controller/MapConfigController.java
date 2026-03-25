package com.example.appbackend.controller;

import com.example.appbackend.dto.MapConfigResponse;
import com.example.appbackend.dto.MapConfigUpdateRequest;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/map/config")
@Tag(name = "地图配置", description = "地图配置项的获取和更新接口")
public class MapConfigController {

    @Autowired
    private MapService mapService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }

    @GetMapping
    @Operation(summary = "获取地图配置", description = "获取地图中心点、缩放级别、边界等配置信息")
    public Result<MapConfigResponse> getMapConfig() {
        MapConfigResponse config = mapService.getMapConfig();
        return Result.success(config);
    }

    @PutMapping
    @Operation(summary = "更新地图配置", description = "更新地图配置项（仅管理员）")
    public Result<Void> updateMapConfig(
            @RequestBody MapConfigUpdateRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        mapService.updateMapConfig(request);
        return Result.success("地图配置更新成功", null);
    }

    @GetMapping("/item/{configKey}")
    @Operation(summary = "获取配置项", description = "根据配置键获取单个配置值")
    public Result<String> getConfigItem(@PathVariable String configKey) {
        String value = mapService.getConfigItem(configKey);
        return Result.success(value);
    }
}
