package com.example.appbackend.controller;

import com.example.appbackend.entity.Announcement;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@Tag(name = "公告管理", description = "公告的增删改查接口")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    private void checkRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }

    @PostMapping
    @Operation(summary = "创建公告", description = "创建新公告，需要管理员或教师权限")
    public Result<Announcement> createAnnouncement(
            @Parameter(description = "公告信息", required = true)
            @RequestBody Announcement announcement,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        Announcement created = announcementService.createAnnouncement(announcement);
        return Result.success("公告创建成功", created);
    }

    @GetMapping("/enabled")
    @Operation(summary = "获取已启用的公告", description = "获取所有已启用的公告，供前端展示用，无需权限")
    public Result<List<Announcement>> getEnabledAnnouncements() {
        List<Announcement> announcements = announcementService.getEnabledAnnouncements();
        return Result.success(announcements);
    }

    @GetMapping
    @Operation(summary = "获取所有公告", description = "获取所有公告（包括禁用），供后台管理，需要管理员或教师权限")
    public Result<List<Announcement>> getAllAnnouncements(HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        List<Announcement> announcements = announcementService.getAllAnnouncements();
        return Result.success(announcements);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取公告详情", description = "根据ID获取公告详细信息")
    public Result<Announcement> getAnnouncementById(
            @Parameter(description = "公告ID", required = true)
            @PathVariable Long id) {
        Announcement announcement = announcementService.getAnnouncementById(id);
        return Result.success(announcement);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新公告", description = "更新指定ID的公告信息，需要管理员或教师权限")
    public Result<Announcement> updateAnnouncement(
            @Parameter(description = "公告ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "公告信息")
            @RequestBody Announcement announcement,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        Announcement updated = announcementService.updateAnnouncement(
                id,
                announcement.getTitle(),
                announcement.getContent(),
                announcement.getSortOrder(),
                announcement.getEnabled(),
                announcement.getIsTop()
        );
        return Result.success("公告更新成功", updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告", description = "删除指定ID的公告，需要管理员或教师权限")
    public Result<Void> deleteAnnouncement(
            @Parameter(description = "公告ID", required = true)
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        announcementService.deleteAnnouncement(id);
        return Result.success("公告删除成功", null);
    }
}
