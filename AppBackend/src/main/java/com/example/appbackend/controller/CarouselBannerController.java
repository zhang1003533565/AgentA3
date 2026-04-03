package com.example.appbackend.controller;

import com.example.appbackend.entity.CarouselBanner;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CarouselBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/carousel-banners")
@Tag(name = "轮播图管理", description = "轮播图的增删改查接口")
public class CarouselBannerController {

    @Autowired
    private CarouselBannerService carouselBannerService;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    private void checkRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "上传轮播图", description = "上传图片并创建轮播图记录，需要管理员或教师权限")
    public Result<CarouselBanner> uploadBanner(
            @Parameter(description = "轮播图图片", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "标题（可选）")
            @RequestParam(required = false) String title,
            @Parameter(description = "跳转链接（可选）")
            @RequestParam(required = false) String linkUrl,
            @Parameter(description = "排序值，值越小越靠前，默认0")
            @RequestParam(required = false, defaultValue = "0") Integer sortOrder,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        CarouselBanner banner = carouselBannerService.uploadBanner(file, title, linkUrl, sortOrder);
        return Result.success("轮播图上传成功", banner);
    }

    @GetMapping("/enabled")
    @Operation(summary = "获取已启用的轮播图", description = "获取所有已启用的轮播图，供前端展示用，无需权限")
    public Result<List<CarouselBanner>> getEnabledBanners() {
        List<CarouselBanner> banners = carouselBannerService.getEnabledBanners();
        return Result.success(banners);
    }

    @GetMapping
    @Operation(summary = "获取所有轮播图", description = "获取所有轮播图（包括禁用），供后台管理，需要管理员或教师权限")
    public Result<List<CarouselBanner>> getAllBanners(HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        List<CarouselBanner> banners = carouselBannerService.getAllBanners();
        return Result.success(banners);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取轮播图详情", description = "根据ID获取轮播图详细信息")
    public Result<CarouselBanner> getBannerById(
            @Parameter(description = "轮播图ID", required = true)
            @PathVariable Long id) {
        CarouselBanner banner = carouselBannerService.getBannerById(id);
        return Result.success(banner);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新轮播图", description = "更新指定ID的轮播图信息，需要管理员或教师权限")
    public Result<CarouselBanner> updateBanner(
            @Parameter(description = "轮播图ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "标题（可选）")
            @RequestParam(required = false) String title,
            @Parameter(description = "跳转链接（可选）")
            @RequestParam(required = false) String linkUrl,
            @Parameter(description = "排序值")
            @RequestParam(required = false) Integer sortOrder,
            @Parameter(description = "是否启用")
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        CarouselBanner banner = carouselBannerService.updateBanner(id, title, linkUrl, sortOrder, enabled);
        return Result.success("轮播图更新成功", banner);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除轮播图", description = "删除指定ID的轮播图，需要管理员或教师权限")
    public Result<Void> deleteBanner(
            @Parameter(description = "轮播图ID", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        carouselBannerService.deleteBanner(id);
        return Result.success("轮播图删除成功", null);
    }
}
