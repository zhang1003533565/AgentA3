package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.AppMessage;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.AppMessageRepository;
import com.example.appbackend.service.AppMessageService;
import com.example.appbackend.service.MessageRealtimeNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AppMessageServiceImpl implements AppMessageService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String EVENT_CHAT_MESSAGE = "CHAT_MESSAGE";
    private static final String SOURCE_CHAT_MESSAGE = "CHAT_MESSAGE";

    @Autowired private AppMessageRepository appMessageRepository;
    @Autowired private MessageRealtimeNotifier realtimeNotifier;

    @Override
    public PageResponse<AppMessageDTO.MessageVO> getMessages(Long userId, Integer current, Integer size) {
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<AppMessage> page = appMessageRepository.findByUserIdOrderByCreateTimeDesc(userId, PageRequest.of(safeCurrent - 1, safeSize));
        List<AppMessageDTO.MessageVO> records = page.getContent().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), safeCurrent, safeSize);
    }

    @Override
    public AppMessageDTO.UnreadCountVO getUnreadCount(Long userId) {
        AppMessageDTO.UnreadCountVO vo = new AppMessageDTO.UnreadCountVO();
        vo.setTotal(appMessageRepository.countByUserIdAndIsReadFalse(userId));
        vo.setLostFound(appMessageRepository.countByUserIdAndModuleTypeAndIsReadFalse(userId, AppMessage.MODULE_LOST_FOUND));
        vo.setExam(appMessageRepository.countByUserIdAndModuleTypeAndIsReadFalse(userId, AppMessage.MODULE_EXAM));
        return vo;
    }

    @Override
    @Transactional
    public void markRead(Long id, Long userId) {
        AppMessage message = appMessageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "消息不存在"));
        if (!userId.equals(message.getUserId())) {
            throw new BusinessException(403, "无权操作该消息");
        }
        if (!Boolean.TRUE.equals(message.getIsRead())) {
            message.setIsRead(true);
            message.setReadTime(LocalDateTime.now());
            appMessageRepository.save(message);
            realtimeNotifier.notifyUser(userId, "app");
        }
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        if (appMessageRepository.markAllReadByUserId(userId) > 0) {
            realtimeNotifier.notifyUser(userId, "app");
        }
    }

    @Override
    @Transactional
    public void markReadByCategory(AppMessageDTO.ReadByCategoryCommand command, Long userId) {
        if (command == null || command.getModuleType() == null || command.getEventTypes() == null || command.getEventTypes().isEmpty()) {
            return;
        }
        if (appMessageRepository.markReadByUserIdAndModuleTypeAndEventTypeIn(
                userId, command.getModuleType(), command.getEventTypes()) > 0) {
            realtimeNotifier.notifyUser(userId, "app");
        }
    }

    @Override
    @Transactional
    public void markLostFoundChatMessagesReadBySession(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return;
        }
        int updated = appMessageRepository.markChatMessagesReadBySession(
                userId,
                sessionId,
                AppMessage.MODULE_LOST_FOUND,
                EVENT_CHAT_MESSAGE,
                SOURCE_CHAT_MESSAGE);
        if (updated > 0) {
            realtimeNotifier.notifyUser(userId, "app");
        }
    }

    @Override
    @Transactional
    public AppMessageDTO.MessageVO createIfAbsent(AppMessageDTO.CreateCommand command) {
        if (command == null || command.getUserId() == null || command.getEventType() == null || command.getSourceId() == null || command.getSourceType() == null) {
            return null;
        }
        Optional<AppMessage> existing = appMessageRepository.findBySourceTypeAndSourceIdAndUserIdAndEventType(
                command.getSourceType(), command.getSourceId(), command.getUserId(), command.getEventType());
        if (existing.isPresent()) {
            return toVO(existing.get());
        }
        AppMessage message = new AppMessage();
        message.setUserId(command.getUserId());
        message.setModuleType(command.getModuleType() == null ? AppMessage.MODULE_LOST_FOUND : command.getModuleType());
        message.setEventType(command.getEventType());
        message.setTitle(command.getTitle() == null ? "消息提醒" : command.getTitle());
        message.setContent(command.getContent());
        message.setTargetPage(command.getTargetPage());
        message.setTargetParams(command.getTargetParams());
        message.setSourceId(command.getSourceId());
        message.setSourceType(command.getSourceType());
        AppMessage saved = appMessageRepository.save(message);
        if (AppMessage.MODULE_EXAM.equals(message.getModuleType())) {
            realtimeNotifier.notifyUser(command.getUserId(), "app", "exam");
        } else {
            realtimeNotifier.notifyUser(command.getUserId(), "app");
        }
        return toVO(saved);
    }

    private AppMessageDTO.MessageVO toVO(AppMessage message) {
        AppMessageDTO.MessageVO vo = new AppMessageDTO.MessageVO();
        vo.setId(message.getId());
        vo.setUserId(message.getUserId());
        vo.setModuleType(message.getModuleType());
        vo.setEventType(message.getEventType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setTargetPage(message.getTargetPage());
        vo.setTargetParams(message.getTargetParams());
        vo.setSourceId(message.getSourceId());
        vo.setSourceType(message.getSourceType());
        vo.setIsRead(message.getIsRead());
        vo.setCreateTime(formatTime(message.getCreateTime()));
        vo.setReadTime(formatTime(message.getReadTime()));
        return vo;
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(TIME_FORMATTER);
    }
}
