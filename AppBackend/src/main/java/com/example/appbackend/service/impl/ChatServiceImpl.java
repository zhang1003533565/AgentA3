package com.example.appbackend.service.impl;

import com.example.appbackend.dto.*;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.*;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.*;
import com.example.appbackend.service.AppMessageService;
import com.example.appbackend.service.ChatService;
import com.example.appbackend.service.MessageRealtimeNotifier;
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
    @Autowired private AppMessageService appMessageService;
    @Autowired private MessageRealtimeNotifier realtimeNotifier;
    @Autowired private ObjectMapper objectMapper;
    @PersistenceContext private EntityManager entityManager;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CONTACT_EXCHANGE_NONE = "NONE";
    private static final String CONTACT_EXCHANGE_REQUESTED = "REQUESTED";
    private static final String CONTACT_EXCHANGE_EXCHANGED = "EXCHANGED";
    private static final String CONTACT_ACTION_DECLINE = "DECLINE";
    private static final int ITEM_ON_SALE = 2;

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
            itemRepository.incrementInquiryCount(itemId);
            itemRepository.updateHeatScore(itemId);
        }
        return toSessionVO(session, userId);
    }

    @Override
    public PageResponse<ChatDTO.SessionVO> getSessionList(Long userId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 20;
        Page<ChatSession> page = sessionRepository.findByUserIdWithMessages(userId, PageRequest.of(current - 1, size));
        List<ChatDTO.SessionVO> records = page.getContent().stream()
                .map(s -> toSessionVO(s, userId)).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDTO.SessionVO getSessionById(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findByIdWithItem(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId)) {
            throw new BusinessException(403, "无权限");
        }
        return toSessionVO(session, userId);
    }

    @Override
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        if (!session.getBuyerId().equals(userId) && !session.getSellerId().equals(userId))
            throw new BusinessException(403, "无权限");
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
        realtimeNotifier.notifyUser(session.getBuyerId(), "chat", "trade", "sessions");
        realtimeNotifier.notifyUser(session.getSellerId(), "chat", "trade", "sessions");
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
        Integer messageType = normalizeUserMessageType(req.getMessageType());
        if (Integer.valueOf(4).equals(messageType)) {
            return handleContactExchange(req, senderId, session);
        }
        msg.setMessageType(messageType);
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
        createChatMessageAppMessageIfNeeded(session, msg);
        notifyMessageChange(session, senderId, true, false);
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
        notifyMessageChange(session, actorId, false, true);
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
        int unreadBefore = sessionRepository.countUnreadBySessionAndUser(sessionId, userId);
        messageRepository.markAllReadBySessionAndUser(sessionId, userId);
        if (userId.equals(session.getBuyerId())) {
            sessionRepository.clearBuyerUnread(sessionId);
        } else {
            sessionRepository.clearSellerUnread(sessionId);
        }
        appMessageService.markLostFoundChatMessagesReadBySession(sessionId, userId);
        if (unreadBefore > 0) {
            realtimeNotifier.notifyUser(userId, "chat", "sessions");
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
    @Transactional(readOnly = true)
    public ChatDTO.MessageSummaryVO getMessageSummary(Long userId, Integer current, Integer size) {
        ChatDTO.MessageSummaryVO summary = new ChatDTO.MessageSummaryVO();
        summary.setSessions(getSessionList(userId, current, size));
        summary.setChatUnreadCount(getUnreadCount(userId));
        summary.setTradeUnreadCount(countUnreadTradeNotifications(userId));
        return summary;
    }

    @Override
    public void markTradeNotificationRead(Long id, Long userId) {
        ChatMessage message = messageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易通知不存在"));
        if (!Integer.valueOf(0).equals(message.getMessageType()) && !Integer.valueOf(4).equals(message.getMessageType())) {
            throw new BusinessException(400, "不是交易通知");
        }
        if (message.getSession() == null ||
                (!message.getSession().getBuyerId().equals(userId) && !message.getSession().getSellerId().equals(userId))) {
            throw new BusinessException(403, "无权限");
        }
        if (!message.getSenderId().equals(userId) && Boolean.FALSE.equals(message.getIsRead())) {
            message.setIsRead(true);
            messageRepository.save(message);
            realtimeNotifier.notifyUser(userId, "trade");
        }
    }

    @Override
    public void markAllTradeNotificationsRead(Long userId) {
        if (messageRepository.markAllTradeNotificationsReadByUser(userId) > 0) {
            realtimeNotifier.notifyUser(userId, "trade");
        }
    }

    // ========== 工具方法 ==========

    private Integer normalizeUserMessageType(Integer messageType) {
        if (messageType == null) return 1;
        if (messageType < 1 || messageType > 4) {
            throw new BusinessException(400, "普通聊天消息类型只能是1-文本、2-图片、3-位置、4-交换联系方式");
        }
        return messageType;
    }

    private ChatDTO.MessageVO handleContactExchange(ChatDTO.SendMessageRequest req, Long senderId, ChatSession session) {
        TradeRecord tradeRecord = ensureTradingRecordForSession(session, senderId);
        normalizeContactExchangeState(tradeRecord);
        if (CONTACT_EXCHANGE_EXCHANGED.equals(tradeRecord.getContactExchangeStatus())) {
            throw new BusinessException(400, "已交换联系方式");
        }

        String action = req.getContactExchangeAction() == null ? "" : req.getContactExchangeAction().trim().toUpperCase(Locale.ROOT);
        if (CONTACT_ACTION_DECLINE.equals(action)) {
            resetContactExchange(tradeRecord);
            tradeRecordRepository.save(tradeRecord);
            ChatMessage declineMessage = saveTradeMessage(session, senderId, "双方可以继续在平台内交流。", 0);
            updateSessionLast(session, senderId, declineMessage.getContent());
            notifyMessageChange(session, senderId, true, true);
            return toMessageVO(declineMessage, senderId);
        }

        String contactContent = req.getContent() == null ? "" : req.getContent().trim();
        if (contactContent.isEmpty()) {
            throw new BusinessException(400, "请选择需要交换的联系方式");
        }

        if (tradeRecord.getContactExchangeRequesterId() == null || CONTACT_EXCHANGE_NONE.equals(tradeRecord.getContactExchangeStatus())) {
            tradeRecord.setContactExchangeRequesterId(senderId);
        }
        setContactAgreement(tradeRecord, senderId, contactContent);

        if (Boolean.TRUE.equals(tradeRecord.getBuyerContactAgreed()) && Boolean.TRUE.equals(tradeRecord.getSellerContactAgreed())) {
            tradeRecord.setContactExchangeConfirmerId(senderId);
            tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_EXCHANGED);
            tradeRecord.setContactExchangeTime(LocalDateTime.now());
            tradeRecordRepository.save(tradeRecord);
            saveContactMessage(session, tradeRecord.getBuyerId(), tradeRecord.getBuyerContactContent());
            saveContactMessage(session, tradeRecord.getSellerId(), tradeRecord.getSellerContactContent());
            ChatMessage doneMessage = saveTradeMessage(session, senderId, "双方已交换联系方式，可以通过线下方式沟通交易。", 0);
            updateSessionLast(session, senderId, doneMessage.getContent());
            createContactExchangeAppMessages(session, tradeRecord);
            notifyMessageChange(session, senderId, true, true);
            realtimeNotifier.notifyUser(senderId, "trade");
            return toMessageVO(doneMessage, senderId);
        }

        tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_REQUESTED);
        tradeRecordRepository.save(tradeRecord);
        return buildContactExchangeStateMessage(session, senderId);
    }

    private TradeRecord ensureTradingRecordForSession(ChatSession session, Long currentUserId) {
        if (session == null || currentUserId == null) {
            throw new BusinessException(400, "会话信息不完整");
        }
        if (!Objects.equals(currentUserId, session.getBuyerId()) && !Objects.equals(currentUserId, session.getSellerId())) {
            throw new BusinessException(403, "无权限操作该交易");
        }
        Optional<TradeRecord> activeRecord = tradeRecordRepository.findByItemIdAndBuyerIdAndStatusIn(
                session.getItemId(),
                session.getBuyerId(),
                Arrays.asList(TradeRecord.TradeStatus.WAIT_CONFIRM, TradeRecord.TradeStatus.TRADING));
        if (activeRecord.isPresent()) {
            TradeRecord tradeRecord = activeRecord.get();
            if (tradeRecord.getStatus() == TradeRecord.TradeStatus.WAIT_CONFIRM) {
                tradeRecord.setStatus(TradeRecord.TradeStatus.TRADING);
                tradeRecord = tradeRecordRepository.save(tradeRecord);
            }
            return tradeRecord;
        }
        SecondhandItem item = itemRepository.findById(session.getItemId())
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        if (!Objects.equals(item.getUserId(), session.getSellerId())) {
            throw new BusinessException(400, "聊天会话与商品发布者不匹配");
        }
        if (!Integer.valueOf(ITEM_ON_SALE).equals(item.getStatus())) {
            throw new BusinessException(400, "商品当前不可交易");
        }
        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setItemId(session.getItemId());
        tradeRecord.setBuyerId(session.getBuyerId());
        tradeRecord.setSellerId(session.getSellerId());
        tradeRecord.setStatus(TradeRecord.TradeStatus.TRADING);
        tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_NONE);
        return tradeRecordRepository.save(tradeRecord);
    }

    private ChatMessage saveTradeMessage(ChatSession session, Long senderId, String content, Integer messageType) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(session.getId());
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setMessageType(messageType);
        msg.setIsRead(false);
        return messageRepository.save(msg);
    }

    private void updateSessionLast(ChatSession session, Long senderId, String content) {
        LocalDateTime now = LocalDateTime.now();
        if (senderId.equals(session.getBuyerId())) {
            sessionRepository.incrementSellerUnreadAndUpdateLast(session.getId(), content, now);
        } else {
            sessionRepository.incrementBuyerUnreadAndUpdateLast(session.getId(), content, now);
        }
    }

    private void saveContactMessage(ChatSession session, Long senderId, String content) {
        if (content == null || content.trim().isEmpty()) return;
        saveTradeMessage(session, senderId, content.trim(), 4);
    }

    private void notifyMessageChange(ChatSession session,
                                     Long actorId,
                                     boolean chatChanged,
                                     boolean tradeChanged) {
        if (session == null || actorId == null) return;
        Long receiverId = Objects.equals(actorId, session.getBuyerId())
                ? session.getSellerId()
                : session.getBuyerId();
        realtimeNotifier.notifyUser(actorId, "sessions");
        if (receiverId == null) return;
        if (chatChanged && tradeChanged) {
            realtimeNotifier.notifyUser(receiverId, "chat", "trade", "sessions");
        } else if (chatChanged) {
            realtimeNotifier.notifyUser(receiverId, "chat", "sessions");
        } else if (tradeChanged) {
            realtimeNotifier.notifyUser(receiverId, "trade", "sessions");
        } else {
            realtimeNotifier.notifyUser(receiverId, "sessions");
        }
    }

    private void createChatMessageAppMessageIfNeeded(ChatSession session, ChatMessage message) {
        if (session == null || message == null || message.getId() == null || message.getSenderId() == null) {
            return;
        }
        Long receiverId = Objects.equals(message.getSenderId(), session.getBuyerId())
                ? session.getSellerId()
                : session.getBuyerId();
        if (receiverId == null || Objects.equals(receiverId, message.getSenderId())) {
            return;
        }
        AppMessageDTO.CreateCommand command = new AppMessageDTO.CreateCommand();
        command.setUserId(receiverId);
        command.setModuleType(AppMessage.MODULE_LOST_FOUND);
        command.setEventType("CHAT_MESSAGE");
        command.setTitle(resolveUserDisplayName(message.getSenderId()));
        command.setContent("[" + resolveItemTitle(session.getItemId()) + "] " + resolveMessageSummary(message));
        command.setTargetPage("/subpackage_lostfound/lostfoundChat/lostfoundChat");
        command.setTargetParams(buildTargetParams(session, null));
        command.setSourceType("CHAT_MESSAGE");
        command.setSourceId(message.getId());
        appMessageService.createIfAbsent(command);
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null) return "对方用户";
        return userRepository.findById(userId)
                .map(user -> firstNonBlank(user.getRealName(), user.getUsername(), "对方用户"))
                .orElse("对方用户");
    }

    private String resolveItemTitle(Long itemId) {
        if (itemId == null) return "商品";
        return itemRepository.findById(itemId)
                .map(item -> firstNonBlank(item.getTitle(), "商品"))
                .orElse("商品");
    }

    private String resolveMessageSummary(ChatMessage message) {
        if (message == null) return "新消息";
        if (Integer.valueOf(2).equals(message.getMessageType())) return "[图片]";
        if (Integer.valueOf(3).equals(message.getMessageType())) return "[位置]";
        return firstNonBlank(message.getContent(), "新消息");
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private void createContactExchangeAppMessages(ChatSession session, TradeRecord tradeRecord) {
        createContactExchangeAppMessage(session, tradeRecord, tradeRecord.getBuyerId());
        createContactExchangeAppMessage(session, tradeRecord, tradeRecord.getSellerId());
    }

    private void createContactExchangeAppMessage(ChatSession session, TradeRecord tradeRecord, Long userId) {
        if (session == null || tradeRecord == null || userId == null) return;
        AppMessageDTO.CreateCommand command = new AppMessageDTO.CreateCommand();
        command.setUserId(userId);
        command.setModuleType(AppMessage.MODULE_LOST_FOUND);
        command.setEventType("CONTACT_EXCHANGE");
        command.setTitle("联系方式已交换");
        command.setContent("双方已交换联系方式，可以继续沟通线下交易");
        command.setTargetPage("/subpackage_lostfound/lostfoundChat/lostfoundChat");
        command.setTargetParams(buildTargetParams(session, tradeRecord.getId()));
        command.setSourceType("TRADE_RECORD");
        command.setSourceId(tradeRecord.getId());
        appMessageService.createIfAbsent(command);
    }

    private String buildTargetParams(ChatSession session, Long tradeId) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("itemId", session.getItemId());
            params.put("sessionId", session.getId());
            if (tradeId != null) {
                params.put("tradeId", tradeId);
            }
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return "{\"itemId\":" + session.getItemId() + ",\"sessionId\":" + session.getId() + "}";
        }
    }

    private ChatDTO.MessageVO buildContactExchangeStateMessage(ChatSession session, Long senderId) {
        ChatDTO.MessageVO vo = new ChatDTO.MessageVO();
        vo.setSessionId(session.getId());
        vo.setSenderId(senderId);
        vo.setContent("");
        vo.setMessageType(0);
        vo.setIsRead(false);
        vo.setIsMine(true);
        vo.setCreateTime(LocalDateTime.now().format(FMT));
        return vo;
    }

    private void setContactAgreement(TradeRecord tradeRecord, Long userId, String contactContent) {
        if (Objects.equals(userId, tradeRecord.getBuyerId())) {
            tradeRecord.setBuyerContactAgreed(true);
            tradeRecord.setBuyerContactContent(contactContent);
            if (Objects.equals(userId, tradeRecord.getContactExchangeRequesterId())) {
                tradeRecord.setRequesterContactContent(contactContent);
            } else {
                tradeRecord.setConfirmerContactContent(contactContent);
            }
        } else if (Objects.equals(userId, tradeRecord.getSellerId())) {
            tradeRecord.setSellerContactAgreed(true);
            tradeRecord.setSellerContactContent(contactContent);
            if (Objects.equals(userId, tradeRecord.getContactExchangeRequesterId())) {
                tradeRecord.setRequesterContactContent(contactContent);
            } else {
                tradeRecord.setConfirmerContactContent(contactContent);
            }
        } else {
            throw new BusinessException(403, "无权限交换联系方式");
        }
    }

    private void resetContactExchange(TradeRecord tradeRecord) {
        tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_NONE);
        tradeRecord.setContactExchangeRequesterId(null);
        tradeRecord.setContactExchangeConfirmerId(null);
        tradeRecord.setBuyerContactAgreed(false);
        tradeRecord.setSellerContactAgreed(false);
        tradeRecord.setBuyerContactContent(null);
        tradeRecord.setSellerContactContent(null);
        tradeRecord.setRequesterContactContent(null);
        tradeRecord.setConfirmerContactContent(null);
        tradeRecord.setContactExchangeTime(null);
    }

    private void normalizeContactExchangeState(TradeRecord tradeRecord) {
        if (CONTACT_EXCHANGE_EXCHANGED.equals(tradeRecord.getContactExchangeStatus())) {
            migrateLegacyContactContent(tradeRecord);
            tradeRecord.setBuyerContactAgreed(true);
            tradeRecord.setSellerContactAgreed(true);
            return;
        }
        if ("PENDING".equals(tradeRecord.getContactExchangeStatus())) {
            migrateLegacyContactContent(tradeRecord);
            if (Objects.equals(tradeRecord.getContactExchangeRequesterId(), tradeRecord.getBuyerId())) {
                tradeRecord.setBuyerContactAgreed(true);
            } else if (Objects.equals(tradeRecord.getContactExchangeRequesterId(), tradeRecord.getSellerId())) {
                tradeRecord.setSellerContactAgreed(true);
            }
            tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_REQUESTED);
        }
        if (tradeRecord.getContactExchangeStatus() == null) {
            tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_NONE);
        }
        if (tradeRecord.getBuyerContactAgreed() == null) {
            tradeRecord.setBuyerContactAgreed(false);
        }
        if (tradeRecord.getSellerContactAgreed() == null) {
            tradeRecord.setSellerContactAgreed(false);
        }
    }

    private void migrateLegacyContactContent(TradeRecord tradeRecord) {
        if (Objects.equals(tradeRecord.getContactExchangeRequesterId(), tradeRecord.getBuyerId())) {
            if (tradeRecord.getBuyerContactContent() == null) tradeRecord.setBuyerContactContent(tradeRecord.getRequesterContactContent());
            if (tradeRecord.getSellerContactContent() == null) tradeRecord.setSellerContactContent(tradeRecord.getConfirmerContactContent());
        } else if (Objects.equals(tradeRecord.getContactExchangeRequesterId(), tradeRecord.getSellerId())) {
            if (tradeRecord.getSellerContactContent() == null) tradeRecord.setSellerContactContent(tradeRecord.getRequesterContactContent());
            if (tradeRecord.getBuyerContactContent() == null) tradeRecord.setBuyerContactContent(tradeRecord.getConfirmerContactContent());
        }
    }

    private String resolveTradeAction(ChatMessage message) {
        if (message == null) return null;
        if (Integer.valueOf(4).equals(message.getMessageType())) return "CONTACT_EXCHANGE_DONE";
        if (!Integer.valueOf(0).equals(message.getMessageType())) return null;
        String content = message.getContent();
        if ("你表达了购买意向，等待对方确认".equals(content)) return "TRADE_INTENT";
        if ("双方已确认线下交易，建议尽快约定时间地点".equals(content)) return "TRADE_CONFIRM";
        if ("双方已交换联系方式，可进行线下沟通交易。".equals(content) ||
                "双方已交换联系方式，可以通过线下方式沟通交易。".equals(content)) return "CONTACT_EXCHANGE_DONE";
        if ("该商品交易已完成".equals(content) || "卖家已标记该商品交易完成".equals(content)) return "TRADE_COMPLETE";
        if ("交易已取消".equals(content) || "本次交易已取消".equals(content)) return "TRADE_CANCEL";
        return null;
    }

    private ChatDTO.SessionVO toSessionVO(ChatSession s, Long currentUserId) {
        ChatDTO.SessionVO vo = new ChatDTO.SessionVO();
        vo.setSessionId(s.getId());
        vo.setItemId(s.getItemId());
        if (s.getItem() != null) {
            vo.setItemTitle(s.getItem().getTitle());
            vo.setItemPrice(s.getItem().getPrice());
            vo.setItemStatus(s.getItem().getStatus());
            vo.setItemTradeType(s.getItem().getTradeType());
            vo.setItemStatusText(getItemStatusText(s.getItem().getStatus(), s.getItem().getTradeType()));
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
                    vo.setContactExchangeStatus(trade.getContactExchangeStatus());
                    vo.setContactExchangeRequesterId(trade.getContactExchangeRequesterId());
                    vo.setContactExchange(buildContactExchangeVO(trade, currentUserId));
                });
        return vo;
    }

    private String getItemStatusText(Integer status, String tradeType) {
        if (status == null) return "";
        switch (status) {
            case 2: return "buy".equals(tradeType) ? "收物" : "出物";
            case 3: return "已售出";
            case 4: return "已下架";
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
        vo.setContent(Integer.valueOf(4).equals(m.getMessageType()) && m.getSenderId().equals(currentUserId) ? "" : m.getContent());
        vo.setMessageType(m.getMessageType());
        vo.setTradeAction(resolveTradeAction(m));
        vo.setIsRead(!m.getSenderId().equals(currentUserId) && Boolean.FALSE.equals(m.getIsRead()));
        vo.setIsMine(m.getSenderId().equals(currentUserId));
        vo.setCreateTime(m.getCreateTime() != null ? m.getCreateTime().format(FMT) : null);
        if (m.getSender() != null) {
            vo.setSenderName(m.getSender().getUsername());
            vo.setSenderAvatar(m.getSender().getAvatar());
        }
        return vo;
    }

    private ChatDTO.ContactExchangeVO buildContactExchangeVO(TradeRecord tradeRecord, Long currentUserId) {
        normalizeContactExchangeState(tradeRecord);
        boolean isBuyer = Objects.equals(currentUserId, tradeRecord.getBuyerId());
        boolean currentAgreed = isBuyer
                ? Boolean.TRUE.equals(tradeRecord.getBuyerContactAgreed())
                : Boolean.TRUE.equals(tradeRecord.getSellerContactAgreed());
        boolean otherAgreed = isBuyer
                ? Boolean.TRUE.equals(tradeRecord.getSellerContactAgreed())
                : Boolean.TRUE.equals(tradeRecord.getBuyerContactAgreed());
        boolean contactExchangeAllowed = tradeRecord.getStatus() == TradeRecord.TradeStatus.WAIT_CONFIRM ||
                tradeRecord.getStatus() == TradeRecord.TradeStatus.TRADING;
        boolean exchanged = CONTACT_EXCHANGE_EXCHANGED.equals(tradeRecord.getContactExchangeStatus());

        ChatDTO.ContactExchangeVO vo = new ChatDTO.ContactExchangeVO();
        vo.setStatus(tradeRecord.getContactExchangeStatus() == null ? CONTACT_EXCHANGE_NONE : tradeRecord.getContactExchangeStatus());
        vo.setCurrentUserAgreed(currentAgreed);
        vo.setOtherUserAgreed(otherAgreed);
        vo.setCanAgree(contactExchangeAllowed && !exchanged && !currentAgreed && tradeRecord.getContactExchangeRequesterId() != null);
        vo.setCanDecline(contactExchangeAllowed && !exchanged && !currentAgreed && tradeRecord.getContactExchangeRequesterId() != null);
        vo.setRequesterId(tradeRecord.getContactExchangeRequesterId());
        return vo;
    }

    private ChatDTO.TradeNotificationVO toTradeNotificationVO(ChatMessage message, Long currentUserId) {
        ChatDTO.TradeNotificationVO vo = new ChatDTO.TradeNotificationVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setContent(message.getContent());
        vo.setTradeAction(resolveTradeAction(message));
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
