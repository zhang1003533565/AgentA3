package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.ChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChatServiceImpl implements ChatService {

    @Autowired private ChatSessionRepository sessionRepository;
    @Autowired private ChatMessageRepository messageRepository;
    @Autowired private SecondhandItemRepository itemRepository;
    @Autowired private TradeRecordRepository tradeRecordRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;
    @PersistenceContext private EntityManager entityManager;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public ChatDTO.SessionVO createOrGetSession(Long itemId, Long buyerId) {
        return createOrGetSession(itemId, buyerId, null);
    }

    @Override
    public ChatDTO.SessionVO createOrGetSession(Long itemId, Long userId, Long targetUserId) {
        SecondhandItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "物品不存在"));
        Long sellerId = item.getUserId();
        Long buyerId = userId;
        if (targetUserId != null) {
            if (!userRepository.existsById(targetUserId)) {
                throw new BusinessException(404, "聊天对象不存在");
            }
            if (sellerId.equals(userId)) {
                buyerId = targetUserId;
            } else if (sellerId.equals(targetUserId)) {
                buyerId = userId;
            } else {
                throw new BusinessException(400, "聊天对象与商品不匹配");
            }
        }
        if (sellerId.equals(buyerId))
            throw new BusinessException(400, "不能与自己聊天");
        Optional<ChatSession> existing = sessionRepository.findByItemIdAndBuyerId(itemId, buyerId);
        ChatSession session;
        if (existing.isPresent()) {
            session = existing.get();
        } else {
            session = new ChatSession();
            session.setItemId(itemId);
            session.setBuyerId(buyerId);
            session.setSellerId(sellerId);
            session.setLastTime(java.time.LocalDateTime.now());
            session = sessionRepository.save(session);
        }
        return toSessionVO(session, userId);
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
        msg.setMessageType(normalizeUserMessageType(req.getMessageType()));
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
    public void createTradeSystemMessage(Long itemId, Long buyerId, Long actorId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(400, "系统消息内容不能为空");
        }
        ChatSession session = sessionRepository.findByItemIdAndBuyerId(itemId, buyerId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!session.getBuyerId().equals(actorId) && !session.getSellerId().equals(actorId)) {
            throw new BusinessException(403, "无权限写入交易系统消息");
        }
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(session.getId());
        msg.setSenderId(actorId);
        msg.setContent(content.trim());
        msg.setMessageType(0);
        msg.setIsRead(false);
        messageRepository.save(msg);
        session.setLastMessage(content.trim());
        session.setLastTime(LocalDateTime.now());
        sessionRepository.save(session);
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatDTO.TradeNotificationVO> getTradeNotifications(Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 20;
        Page<ChatMessage> page = messageRepository.findTradeNotificationsByUser(userId, PageRequest.of(current - 1, size));
        List<ChatDTO.TradeNotificationVO> records = page.getContent().stream()
                .map(message -> toTradeNotificationVO(message, userId))
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadTradeNotifications(Long userId) {
        return messageRepository.countUnreadTradeNotifications(userId);
    }

    @Override
    public void markTradeNotificationRead(Long id, Long userId) {
        ChatMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易通知不存在"));
        if (!Integer.valueOf(0).equals(message.getMessageType())) {
            throw new BusinessException(400, "不是交易通知");
        }
        if (message.getSession() == null ||
                (!message.getSession().getBuyerId().equals(userId) && !message.getSession().getSellerId().equals(userId))) {
            throw new BusinessException(403, "无权限");
        }
        if (!message.getSenderId().equals(userId) && Boolean.FALSE.equals(message.getIsRead())) {
            message.setIsRead(true);
            messageRepository.save(message);
        }
    }

    // ========== 工具方法 ==========

    private Integer normalizeUserMessageType(Integer messageType) {
        if (messageType == null) return 1;
        if (messageType < 1 || messageType > 3) {
            throw new BusinessException(400, "普通聊天消息类型只能是1-文本、2-图片、3-位置");
        }
        return messageType;
    }

    private ChatDTO.SessionVO toSessionVO(ChatSession s, Long currentUserId) {
        ChatDTO.SessionVO vo = new ChatDTO.SessionVO();
        vo.setSessionId(s.getId());
        vo.setItemId(s.getItemId());
        if (s.getItem() != null) {
            vo.setItemTitle(s.getItem().getTitle());
            vo.setItemPrice(s.getItem().getPrice());
            vo.setItemStatus(s.getItem().getStatus());
            vo.setItemStatusText(getItemStatusText(s.getItem().getStatus()));
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
        tradeRecordRepository.findByItemIdAndBuyerIdAndStatusIn(s.getItemId(), s.getBuyerId(),
                Arrays.asList(TradeRecord.TradeStatus.WAIT_CONFIRM, TradeRecord.TradeStatus.TRADING))
                .ifPresent(trade -> {
                    vo.setTradeId(trade.getId());
                    vo.setTradeStatus(trade.getStatus() != null ? trade.getStatus().name() : null);
                    vo.setTradeStatusText(getTradeStatusText(trade.getStatus()));
                });
        return vo;
    }

    private String getItemStatusText(Integer status) {
        if (status == null) return "";
        switch (status) {
            case 2: return "在售";
            case 3: return "已售出";
            case 4: return "已下架";
            case 5: return "交易中";
            default: return "";
        }
    }

    private String getTradeStatusText(TradeRecord.TradeStatus status) {
        if (status == null) return "";
        switch (status) {
            case WAIT_CONFIRM: return "等待卖家确认";
            case TRADING: return "交易中";
            case COMPLETED: return "交易完成";
            case CANCELLED: return "交易取消";
            default: return "";
        }
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

    private ChatDTO.TradeNotificationVO toTradeNotificationVO(ChatMessage message, Long currentUserId) {
        ChatDTO.TradeNotificationVO vo = new ChatDTO.TradeNotificationVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setContent(message.getContent());
        vo.setCreateTime(message.getCreateTime() != null ? message.getCreateTime().format(FMT) : null);
        vo.setIsRead(message.getSenderId().equals(currentUserId) || Boolean.TRUE.equals(message.getIsRead()));

        ChatSession session = message.getSession();
        if (session != null) {
            vo.setItemId(session.getItemId());
            if (session.getItem() != null) {
                vo.setItemTitle(session.getItem().getTitle());
                vo.setItemImage(readFirstImage(session.getItem().getImages()));
            }
            findLatestTradeRecord(session.getItemId(), session.getBuyerId())
                    .ifPresent(trade -> {
                        vo.setTradeId(trade.getId());
                        vo.setTradeStatus(trade.getStatus() != null ? trade.getStatus().name() : null);
                        vo.setTradeStatusText(getTradeStatusText(trade.getStatus()));
                    });
        }
        return vo;
    }

    private Optional<TradeRecord> findLatestTradeRecord(Long itemId, Long buyerId) {
        List<TradeRecord> records = entityManager.createQuery(
                        "SELECT tr FROM TradeRecord tr WHERE tr.itemId = :itemId AND tr.buyerId = :buyerId ORDER BY tr.updateTime DESC",
                        TradeRecord.class)
                .setParameter("itemId", itemId)
                .setParameter("buyerId", buyerId)
                .setMaxResults(1)
                .getResultList();
        return records.isEmpty() ? Optional.empty() : Optional.of(records.get(0));
    }

    private String readFirstImage(String images) {
        if (images == null || images.isEmpty()) return null;
        try {
            List<String> imgList = objectMapper.readValue(images, new TypeReference<List<String>>() {});
            return imgList.isEmpty() ? null : imgList.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }
}
