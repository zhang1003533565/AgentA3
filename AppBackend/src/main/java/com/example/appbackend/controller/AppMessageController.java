package com.example.appbackend.controller;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AppMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app-message")
@Tag(name = "APP消息中心", description = "APP消息聚合接口")
public class AppMessageController {

    @Autowired private AppMessageService appMessageService;

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }

    @GetMapping("/list")
    @Operation(summary = "消息中心列表")
    public Result<PageResponse<AppMessageDTO.MessageVO>> getMessages(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(appMessageService.getMessages(getUserId(httpRequest), current, size));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "消息中心未读数量")
    public Result<AppMessageDTO.UnreadCountVO> getUnreadCount(HttpServletRequest httpRequest) {
        return Result.success(appMessageService.getUnreadCount(getUserId(httpRequest)));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记消息已读")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest httpRequest) {
        appMessageService.markRead(id, getUserId(httpRequest));
        return Result.success("标记成功", (Void) null);
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public Result<Void> markAllRead(HttpServletRequest httpRequest) {
        appMessageService.markAllRead(getUserId(httpRequest));
        return Result.success("标记成功", (Void) null);
    }

    @PutMapping("/read-by-category")
    @Operation(summary = "按模块和事件类型批量标记已读")
    public Result<Void> markReadByCategory(@RequestBody AppMessageDTO.ReadByCategoryCommand command,
                                           HttpServletRequest httpRequest) {
        appMessageService.markReadByCategory(command, getUserId(httpRequest));
        return Result.success("标记成功", (Void) null);
    }
}
