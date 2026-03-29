package com.example.appbackend.service;

import com.example.appbackend.dto.*;

public interface ChatService {

    // ========== 会话 ==========
    ChatDTO.SessionVO createOrGetSession(Long itemId, Long buyerId);
    PageResponse<ChatDTO.SessionVO> getSessionList(Long userId, Integer current, Integer size);
    void deleteSession(Long sessionId, Long userId);

    // ========== 消息 ==========
    ChatDTO.MessageVO sendMessage(ChatDTO.SendMessageRequest req, Long senderId);
    PageResponse<ChatDTO.MessageVO> getHistoryMessages(Long sessionId, Long userId, Integer current, Integer size);
    long getUnreadCount(Long userId);
}
