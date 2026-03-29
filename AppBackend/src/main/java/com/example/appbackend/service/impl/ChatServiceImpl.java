package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.ChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChatServiceImpl implements ChatService {

    @Autowired private ChatSessionRepository sessionRepository;
    @Autowired private ChatMessageRepository messageRepository;
    @Autowired private SecondhandItemRepository itemRepository;
    @Autowired private ObjectMapper objectMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ChatDTO.SessionVO createOrGetSession(Long itemId, Long buyerId) {
        SecondhandItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        if (item.getUserId().equals(buyerId))
            throw new BusinessException(400, "不能与自己聊天");
        Optional<ChatSession> existing = sessionRepository.findByItemIdAndBuyerId(itemId, buyerId);
        ChatSession session;
        if (existing.isPresent()) {
            session = existing.get();
        } else {
            session = new ChatSession();
            session.setItemId(itemId);
            session.setBuyerId(buyerId);
            session.setSellerId(item.getUserId());
            session.setLastTime(java.time.LocalDateTime.now());
            session = sessionRepository.save(session);
        }
        return toSessionVO(session, buyerId);
    }

    @Override
    public PageResponse<ChatDTO.SessionVO> getSessionList(Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 20;
        Page<ChatSession> page = sessionRepository.findByUserId(userId, PageRequest.of(current - 1, size));
        List<ChatDTO.SessionVO> records = page.getContent().stream()
                .map(s -> toSessionVO(s, userId)).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId))
            throw new BusinessException(403, "无权限");
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
    }

    @Override
    public ChatDTO.MessageVO sendMessage(ChatDTO.SendMessageRequest req, Long senderId) {
        ChatSession session = sessionRepository.findById(req.getSessionId())
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!session.getBuyerId().equals(senderId) && !session.getSellerId().equals(senderId))
            throw new BusinessException(403, "无权限");
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(req.getSessionId());
        msg.setSenderId(senderId);
        msg.setContent(req.getContent());
        msg.setMessageType(req.getMessageType() != null ? req.getMessageType() : 1);
        msg = messageRepository.save(msg);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        // 原子更新会话：用 senderId 和 buyerId 对比，决定给谁 +1
        if (senderId.equals(session.getBuyerId())) {
            sessionRepository.incrementSellerUnreadAndUpdateLast(
                    session.getId(), req.getContent(), now);
        } else {
            sessionRepository.incrementBuyerUnreadAndUpdateLast(
                    session.getId(), req.getContent(), now);
        }
        return toMessageVO(msg, senderId);
    }

    @Override
    public PageResponse<ChatDTO.MessageVO> getHistoryMessages(Long sessionId, Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 20;
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId))
            throw new BusinessException(403, "无权限");
        // 原子标记已读 + 重置计数，同一个事务内完成
        messageRepository.markAllReadBySessionAndUser(sessionId, userId);
        if (userId.equals(session.getBuyerId())) {
            sessionRepository.clearBuyerUnread(sessionId);
        } else {
            sessionRepository.clearSellerUnread(sessionId);
        }

        Page<ChatMessage> page = messageRepository.findBySessionIdOrderByCreateTimeDesc(sessionId, PageRequest.of(current - 1, size));
        List<ChatDTO.MessageVO> records = page.getContent().stream()
                .map(m -> toMessageVO(m, userId)).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return sessionRepository.sumUnreadByUser(userId);
    }

    // ========== 工具方法 ==========

    private ChatDTO.SessionVO toSessionVO(ChatSession s, Long currentUserId) {
        ChatDTO.SessionVO vo = new ChatDTO.SessionVO();
        vo.setSessionId(s.getId());
        vo.setItemId(s.getItemId());
        if (s.getItem() != null) {
            vo.setItemTitle(s.getItem().getTitle());
            vo.setItemPrice(s.getItem().getPrice());
            String images = s.getItem().getImages();
            if (images != null && !images.isEmpty()) {
                try {
                    List<String> imgList = objectMapper.readValue(images, new TypeReference<List<String>>() {});
                    if (!imgList.isEmpty()) vo.setItemImage(imgList.get(0));
                } catch (Exception ignored) {}
            }
        }
        vo.setLastMessage(s.getLastMessage());
        vo.setLastTime(s.getLastTime() != null ? s.getLastTime().format(FMT) : null);
        Long otherUserId = currentUserId.equals(s.getBuyerId()) ? s.getSellerId() : s.getBuyerId();
        vo.setOtherUserId(otherUserId);
        if (s.getBuyer() != null && s.getBuyer().getId().equals(otherUserId)) {
            vo.setOtherUsername(s.getBuyer().getUsername());
            vo.setOtherAvatar(s.getBuyer().getAvatar());
        } else if (s.getSeller() != null && s.getSeller().getId().equals(otherUserId)) {
            vo.setOtherUsername(s.getSeller().getUsername());
            vo.setOtherAvatar(s.getSeller().getAvatar());
        }
        vo.setIsSeller(currentUserId.equals(s.getSellerId()));
        if (vo.getIsSeller()) {
            vo.setUnreadCount(s.getSellerUnreadCount());
        } else {
            vo.setUnreadCount(s.getBuyerUnreadCount());
        }
        return vo;
    }

    private ChatDTO.MessageVO toMessageVO(ChatMessage m, Long currentUserId) {
        ChatDTO.MessageVO vo = new ChatDTO.MessageVO();
        vo.setId(m.getId());
        vo.setSessionId(m.getSessionId());
        vo.setSenderId(m.getSenderId());
        vo.setContent(m.getContent());
        vo.setMessageType(m.getMessageType());
        vo.setIsRead(!m.getSenderId().equals(currentUserId) && Boolean.FALSE.equals(m.getIsRead()));
        vo.setIsMine(m.getSenderId().equals(currentUserId));
        vo.setCreateTime(m.getCreateTime() != null ? m.getCreateTime().format(FMT) : null);
        if (m.getSender() != null) {
            vo.setSenderName(m.getSender().getUsername());
            vo.setSenderAvatar(m.getSender().getAvatar());
        }
        return vo;
    }
}
