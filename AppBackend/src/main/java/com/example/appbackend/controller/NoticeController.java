package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notices")
@Tag(name = "通知管理", description = "通知接口")
public class NoticeController {

    @Operation(summary = "通知列表", description = "获取当前用户的通知列表")
    @GetMapping
    public Result<Object> getNoticeList() {
        return Result.success();
    }

    @Operation(summary = "标记已读", description = "标记通知为已读")
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        return Result.success();
    }

    @Operation(summary = "全部已读", description = "标记所有通知为已读")
    @PutMapping("/read-all")
    public Result<Void> markAllAsRead() {
        return Result.success();
    }

    @Operation(summary = "未读数量", description = "获取未读通知数量")
    @GetMapping("/unread-count")
    public Result<Object> getUnreadCount() {
        return Result.success();
    }
}
