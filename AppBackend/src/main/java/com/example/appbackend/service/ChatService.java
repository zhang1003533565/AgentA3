package com.example.appbackend.service;

import com.example.appbackend.dto.*;

public interface ChatService {

    // ========== 会话 ==========
    ChatDTO.SessionVO createOrGetSession(Long itemId, Long buyerId);
    ChatDTO.SessionVO createOrGetSession(Long itemId, Long userId, Long targetUserId);
    PageResponse<ChatDTO.SessionVO> getSessionList(Long userId, Integer current, Integer size);
    void deleteSession(Long sessionId, Long userId);

    // ========== 消息 ==========
    ChatDTO.MessageVO sendMessage(ChatDTO.SendMessageRequest req, Long senderId);
    void createTradeSystemMessage(Long itemId, Long buyerId, Long actorId, String content);
    PageResponse<ChatDTO.MessageVO> getHistoryMessages(Long sessionId, Long userId, Integer current, Integer size);
    long getUnreadCount(Long userId);
    PageResponse<ChatDTO.TradeNotificationVO> getTradeNotifications(Long userId, Integer current, Integer size);
    long countUnreadTradeNotifications(Long userId);
    void markTradeNotificationRead(Long id, Long userId);
    void markAllTradeNotificationsRead(Long userId);
}
