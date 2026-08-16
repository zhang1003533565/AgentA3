package com.example.appbackend.service;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.PageResponse;

public interface AppMessageService {

    PageResponse<AppMessageDTO.MessageVO> getMessages(Long userId, Integer current, Integer size);

    AppMessageDTO.UnreadCountVO getUnreadCount(Long userId);

    void markRead(Long id, Long userId);

    void markAllRead(Long userId);

    void markReadByCategory(AppMessageDTO.ReadByCategoryCommand command, Long userId);

    void markLostFoundChatMessagesReadBySession(Long sessionId, Long userId);

    AppMessageDTO.MessageVO createIfAbsent(AppMessageDTO.CreateCommand command);
}
