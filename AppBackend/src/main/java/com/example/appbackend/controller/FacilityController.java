package com.example.appbackend.controller;

import com.example.appbackend.dto.FacilityRequest;
import com.example.appbackend.dto.FacilityTypeItem;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.CampusFacility;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.FacilityService;
import com.example.appbackend.service.FacilityTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/facility")
@Tag(name = "设施管理", description = "校园设施的增删改查接口")
public class FacilityController {

    @Autowired
    private FacilityService facilityService;

    @Autowired
    private FacilityTypeService facilityTypeService;

    private static final String ROLE_ADMIN = "ADMIN";

    private void checkAdminRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作");
        }
    }

    @GetMapping("/types")
    @Operation(summary = "获取设施类型字典", description = "返回可维护的设施类型列表，前后端统一使用")
    public Result<java.util.List<FacilityTypeItem>> getFacilityTypes() {
        return Result.success(facilityTypeService.listTypes());
    }

    @PutMapping("/types")
    @Operation(summary = "更新设施类型字典", description = "管理员维护设施类型，保存后全端生效")
    public Result<java.util.List<FacilityTypeItem>> updateFacilityTypes(
            @RequestBody java.util.List<FacilityTypeItem> types,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        facilityTypeService.saveTypes(types);
        return Result.success("设施类型已更新", facilityTypeService.listTypes());
    }

    @GetMapping("/list")
    @Operation(summary = "获取设施列表", description = "分页查询设施列表，支持类型、名称、状态筛选")
    public Result<PageResponse<CampusFacility>> getFacilityList(
            @Parameter(description = "设施类型编码，见 /facility/types")
            @RequestParam(required = false) Integer type,
            @Parameter(description = "设施名称（模糊查询）")
            @RequestParam(required = false) String name,
            @Parameter(description = "设施状态：1-正常/开放 2-维护中 3-关闭/不可用")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认10")
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResponse<CampusFacility> result = facilityService.getFacilityList(type, name, status, pageNum, pageSize);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取设施详情", description = "根据ID获取设施信息（含图片列表）")
    public Result<CampusFacility> getFacilityDetail(@PathVariable Long id) {
        CampusFacility facility = facilityService.getFacilityById(id);
        return Result.success(facility);
    }

    @PostMapping
    @Operation(summary = "新增设施", description = "创建新设施，自动创建关联地图标记")
    public Result<CampusFacility> createFacility(
            @Validated(FacilityRequest.Create.class) @RequestBody FacilityRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        CampusFacility facility = facilityService.createFacility(request);
        return Result.success("设施创建成功", facility);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新设施", description = "更新指定ID的设施信息")
    public Result<CampusFacility> updateFacility(
            @PathVariable Long id,
            @Valid @RequestBody FacilityRequest request,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        CampusFacility facility = facilityService.updateFacility(id, request);
        return Result.success("设施更新成功", facility);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除设施", description = "物理删除指定设施及其关联评价、地图标记与导航/收藏记录")
    public Result<Void> deleteFacility(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdminRole(httpRequest);
        facilityService.deleteFacility(id);
        return Result.success("设施删除成功", null);
    }
}
