package com.example.appbackend.controller;

import com.example.appbackend.dto.*;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "聊天", description = "聊天会话和消息接口")
public class ChatController {

    @Autowired private ChatService chatService;

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }

    // ========== 会话 ==========

    @PostMapping("/session/{itemId}")
    @Operation(summary = "创建/获取会话", description = "根据物品创建会话；支持卖家通过 targetUserId 指定买家")
    public Result<ChatDTO.SessionVO> createOrGetSession(
            @PathVariable Long itemId,
            @RequestParam(required = false) Long targetUserId,
            HttpServletRequest httpRequest) {
        return Result.success(chatService.createOrGetSession(itemId, getUserId(httpRequest), targetUserId));
    }

    @GetMapping("/session/list")
    @Operation(summary = "聊天列表", description = "获取当前用户所有会话")
    public Result<PageResponse<ChatDTO.SessionVO>> getSessionList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(chatService.getSessionList(getUserId(httpRequest), current, size));
    }

    @GetMapping("/session/{sessionId}")
    @Operation(summary = "会话详情", description = "获取单个会话详情（含商品信息）")
    public Result<ChatDTO.SessionVO> getSessionById(
            @PathVariable Long sessionId,
            HttpServletRequest httpRequest) {
        return Result.success(chatService.getSessionById(sessionId, getUserId(httpRequest)));
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "删除会话", description = "删除会话，同时删除该会话下的所有消息")
    public Result<Void> deleteSession(
            @PathVariable Long sessionId,
            HttpServletRequest httpRequest) {
        chatService.deleteSession(sessionId, getUserId(httpRequest));
        return Result.success("删除成功", (Void) null);
    }

    // ========== 消息 ==========

    @PostMapping("/message")
    @Operation(summary = "发送消息")
    public Result<ChatDTO.MessageVO> sendMessage(
            @Valid @RequestBody ChatDTO.SendMessageRequest req,
            HttpServletRequest httpRequest) {
        return Result.success("发送成功", chatService.sendMessage(req, getUserId(httpRequest)));
    }

    @GetMapping("/message/list/{sessionId}")
    @Operation(summary = "历史消息", description = "同时将未读消息标记为已读")
    public Result<PageResponse<ChatDTO.MessageVO>> getHistoryMessages(
            @PathVariable Long sessionId,
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(chatService.getHistoryMessages(sessionId, getUserId(httpRequest), current, size));
    }

    @GetMapping("/message/unread/count")
    @Operation(summary = "未读消息总数")
    public Result<Long> getUnreadCount(HttpServletRequest httpRequest) {
        return Result.success(chatService.getUnreadCount(getUserId(httpRequest)));
    }

    // ========== 交易通知 ==========

    @GetMapping("/trade-notifications")
    @Operation(summary = "交易通知列表", description = "获取当前用户的系统交易消息")
    public Result<PageResponse<ChatDTO.TradeNotificationVO>> getTradeNotifications(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(chatService.getTradeNotifications(getUserId(httpRequest), current, size));
    }

    @GetMapping("/trade-notifications/unread/count")
    @Operation(summary = "交易通知未读数量")
    public Result<Long> getUnreadTradeNotificationCount(HttpServletRequest httpRequest) {
        return Result.success(chatService.countUnreadTradeNotifications(getUserId(httpRequest)));
    }

    @GetMapping("/messages/summary")
    @Operation(summary = "消息概览", description = "聚合返回聊天会话列表、聊天未读数、交易通知未读数，减少前端请求次数")
    public Result<ChatDTO.MessageSummaryVO> getMessageSummary(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "30") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(chatService.getMessageSummary(getUserId(httpRequest), current, size));
    }

    @PutMapping("/trade-notifications/{id}/read")
    @Operation(summary = "标记交易通知已读")
    public Result<Void> markTradeNotificationRead(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        chatService.markTradeNotificationRead(id, getUserId(httpRequest));
        return Result.success("标记成功", (Void) null);
    }

    @PutMapping("/trade-notifications/read-all")
    @Operation(summary = "全部标记交易通知已读")
    public Result<Void> markAllTradeNotificationsRead(HttpServletRequest httpRequest) {
        chatService.markAllTradeNotificationsRead(getUserId(httpRequest));
        return Result.success("标记成功", (Void) null);
    }
}
