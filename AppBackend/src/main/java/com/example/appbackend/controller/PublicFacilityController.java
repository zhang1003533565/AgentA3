package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.PublicFacilityRequest;
import com.example.appbackend.entity.PublicFacility;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.PublicFacilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/public-facilities")
@RequiredArgsConstructor
@Tag(name = "公共设施管理", description = "公共设施的增删改查接口")
public class PublicFacilityController {

    private final PublicFacilityService publicFacilityService;

    @GetMapping
    @Operation(summary = "分页查询公共设施")
    public Result<PageResponse<PublicFacility>> getFacilities(
            @Parameter(description = "设施类型") @RequestParam(required = false) String type,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "排序字段") @RequestParam(required = false) String sortBy,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer size) {
        try {
            PageResponse<PublicFacility> result = publicFacilityService.getFacilitiesPage(type, keyword, sortBy, page, size);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/all")
    @Operation(summary = "获取全部公共设施列表")
    public Result<List<PublicFacility>> getAllFacilities() {
        try {
            List<PublicFacility> result = publicFacilityService.getFacilitiesByType("ALL");
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/by-type")
    @Operation(summary = "按类型获取公共设施列表")
    public Result<List<PublicFacility>> getFacilitiesByType(
            @Parameter(description = "设施类型") @RequestParam String type) {
        try {
            List<PublicFacility> result = publicFacilityService.getFacilitiesByType(type);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取公共设施详情")
    public Result<PublicFacility> getFacilityDetail(
            @Parameter(description = "设施ID") @PathVariable Long id) {
        try {
            PublicFacility facility = publicFacilityService.getFacility(id);
            return Result.success(facility);
        } catch (IllegalArgumentException e) {
            return Result.notFound(e.getMessage());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "新增公共设施")
    public Result<PublicFacility> createFacility(
            @Valid @RequestBody PublicFacilityRequest request) {
        try {
            PublicFacility facility = publicFacilityService.createFacility(request);
            return Result.success(facility);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新公共设施")
    public Result<PublicFacility> updateFacility(
            @Parameter(description = "设施ID") @PathVariable Long id,
            @Valid @RequestBody PublicFacilityRequest request) {
        try {
            PublicFacility facility = publicFacilityService.updateFacility(id, request);
            return Result.success(facility);
        } catch (IllegalArgumentException e) {
            return Result.notFound(e.getMessage());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公共设施")
    public Result<Void> deleteFacility(
            @Parameter(description = "设施ID") @PathVariable Long id) {
        try {
            publicFacilityService.deleteFacility(id);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.notFound(e.getMessage());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
