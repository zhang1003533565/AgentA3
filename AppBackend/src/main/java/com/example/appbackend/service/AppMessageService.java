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

    /**
     * 创建消息；若同来源同事件的消息已存在，则重置为未读（供点赞等聚合类通知复用）。
     */
    AppMessageDTO.MessageVO createOrRefreshUnread(AppMessageDTO.CreateCommand command);

    /**
     * 按来源删除消息（用于取消点赞时清理对应通知）。
     */
    void deleteBySource(String sourceType, Long sourceId, Long userId, String eventType);
}
