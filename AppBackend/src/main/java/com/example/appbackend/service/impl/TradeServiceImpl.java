package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.ChatDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.ChatSession;
import com.example.appbackend.entity.SecondhandItem;
import com.example.appbackend.entity.TradeRecord;
import com.example.appbackend.entity.TradeRecord.TradeStatus;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ChatSessionRepository;
import com.example.appbackend.repository.SecondhandItemRepository;
import com.example.appbackend.repository.TradeRecordRepository;
import com.example.appbackend.service.AppMessageService;
import com.example.appbackend.service.ChatService;
import com.example.appbackend.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class TradeServiceImpl implements TradeService {

    @Autowired private TradeRecordRepository tradeRecordRepository;
    @Autowired private SecondhandItemRepository itemRepository;
    @Autowired private ChatSessionRepository sessionRepository;
    @Autowired private ChatService chatService;
    @Autowired private AppMessageService appMessageService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<TradeStatus> ACTIVE_STATUSES = Arrays.asList(TradeStatus.WAIT_CONFIRM, TradeStatus.TRADING);
    private static final int ITEM_ON_SALE = 2;
    private static final int ITEM_SOLD = 3;
    private static final String CONTACT_EXCHANGE_NONE = "NONE";
    private static final String CONTACT_EXCHANGE_REQUESTED = "REQUESTED";
    private static final String CONTACT_EXCHANGE_EXCHANGED = "EXCHANGED";

    @Override
    public ChatDTO.TradeRecordVO createTradeRecord(ChatDTO.CreateTradeRecordRequest req, Long currentUserId) {
        if (!Objects.equals(req.getBuyerId(), currentUserId)) {
            throw new BusinessException(403, "只能为当前用户创建交易记录");
        }
        return reserveTrade(req.getItemId(), currentUserId);
    }

    @Override
    public ChatDTO.TradeRecordVO reserveTrade(Long itemId, Long currentUserId) {
        SecondhandItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        if (Objects.equals(item.getUserId(), currentUserId)) {
            throw new BusinessException(400, "不能对自己发布的商品表达购买意向");
        }
        if (!Integer.valueOf(ITEM_ON_SALE).equals(item.getStatus())) {
            throw new BusinessException(400, "商品当前不可表达购买意向");
        }
        if (tradeRecordRepository.findByItemIdAndBuyerIdAndStatusIn(itemId, currentUserId, ACTIVE_STATUSES).isPresent()) {
            throw new BusinessException(400, "你已对该商品表达购买意向");
        }

        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setItemId(itemId);
        tradeRecord.setBuyerId(currentUserId);
        tradeRecord.setSellerId(item.getUserId());
        tradeRecord.setStatus(TradeStatus.WAIT_CONFIRM);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "你表达了购买意向，等待对方确认");
        createLostFoundAppMessage(tradeRecord, tradeRecord.getSellerId(), "TRADE_INTENT", "收到购买意向", "有人想购买你的商品");
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    public ChatDTO.TradeRecordVO ensureTradingRecordForSession(Long sessionId, Long currentUserId) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
        TradeRecord tradeRecord = ensureTradingRecord(session, currentUserId);
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    public ChatDTO.TradeRecordVO confirmTrade(Long id, Long currentUserId) {
        TradeRecord tradeRecord = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));
        if (!Objects.equals(currentUserId, tradeRecord.getSellerId())) {
            throw new BusinessException(403, "仅卖家可确认交易");
        }
        if (tradeRecord.getStatus() != TradeStatus.WAIT_CONFIRM) {
            throw new BusinessException(400, "只有待确认交易可以确认");
        }
        tradeRecord.setStatus(TradeStatus.TRADING);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "双方已确认线下交易，建议尽快约定时间地点");
        createLostFoundAppMessage(tradeRecord, tradeRecord.getBuyerId(), "TRADE_CONFIRM", "交易已确认", "卖家已确认交易，请继续沟通交易细节");
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    public ChatDTO.TradeRecordVO completeTrade(Long id, Long currentUserId) {
        TradeRecord tradeRecord = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));
        if (!Objects.equals(currentUserId, tradeRecord.getSellerId())) {
            throw new BusinessException(403, "仅卖家可标记交易完成");
        }
        if (tradeRecord.getStatus() != TradeStatus.TRADING) {
            throw new BusinessException(400, "只有交易中记录可以完成");
        }
        int updated = itemRepository.updateStatusIfCurrent(tradeRecord.getItemId(), ITEM_ON_SALE, ITEM_SOLD);
        if (updated != 1) {
            throw new BusinessException(400, "商品状态异常，无法完成交易");
        }
        tradeRecord.setStatus(TradeStatus.COMPLETED);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        cancelOtherActiveTrades(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "卖家已标记该商品交易完成");
        createLostFoundAppMessage(tradeRecord, tradeRecord.getBuyerId(), "TRADE_COMPLETE", "交易已完成", "卖家已标记该商品交易完成");
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    public ChatDTO.TradeRecordVO cancelTrade(Long id, Long currentUserId) {
        TradeRecord tradeRecord = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));
        checkParticipant(tradeRecord, currentUserId);
        if (tradeRecord.getStatus() != TradeStatus.WAIT_CONFIRM && tradeRecord.getStatus() != TradeStatus.TRADING) {
            throw new BusinessException(400, "当前交易状态不可取消");
        }
        tradeRecord.setStatus(TradeStatus.CANCELLED);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "本次交易已取消");
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatDTO.TradeRecordVO getTradeRecord(Long id, Long currentUserId) {
        TradeRecord tradeRecord = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));
        checkParticipant(tradeRecord, currentUserId);
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatDTO.TradeRecordVO> getTradeList(Long currentUserId, Integer current, Integer size) {
        if (current == null) current = 1;
        if (size == null) size = 20;
        Page<TradeRecord> page = tradeRecordRepository.findByUserId(currentUserId, PageRequest.of(current - 1, size));
        List<ChatDTO.TradeRecordVO> records = page.getContent().stream()
                .map(record -> toVO(record, currentUserId))
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotalElements(), current, size);
    }

    private void checkParticipant(TradeRecord tradeRecord, Long currentUserId) {
        if (!Objects.equals(currentUserId, tradeRecord.getBuyerId()) && !Objects.equals(currentUserId, tradeRecord.getSellerId())) {
            throw new BusinessException(403, "无权限访问该交易记录");
        }
    }

    private TradeRecord ensureTradingRecord(ChatSession session, Long currentUserId) {
        if (!Objects.equals(currentUserId, session.getBuyerId()) && !Objects.equals(currentUserId, session.getSellerId())) {
            throw new BusinessException(403, "无权限操作该交易");
        }
        return tradeRecordRepository.findByItemIdAndBuyerIdAndStatusIn(
                session.getItemId(),
                session.getBuyerId(),
                ACTIVE_STATUSES)
                .map(tradeRecord -> {
                    if (tradeRecord.getStatus() == TradeStatus.WAIT_CONFIRM) {
                        tradeRecord.setStatus(TradeStatus.TRADING);
                        return tradeRecordRepository.save(tradeRecord);
                    }
                    return tradeRecord;
                })
                .orElseGet(() -> createTradingRecord(session));
    }

    private TradeRecord createTradingRecord(ChatSession session) {
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
        tradeRecord.setStatus(TradeStatus.TRADING);
        tradeRecord.setContactExchangeStatus(CONTACT_EXCHANGE_NONE);
        return tradeRecordRepository.save(tradeRecord);
    }

    private void ensureItemStatus(Long itemId, Integer expectedStatus, String message) {
        SecondhandItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        if (!Objects.equals(item.getStatus(), expectedStatus)) {
            throw new BusinessException(400, message);
        }
    }

    private ChatDTO.TradeRecordVO toVO(TradeRecord tradeRecord, Long currentUserId) {
        ChatDTO.TradeRecordVO vo = new ChatDTO.TradeRecordVO();
        vo.setId(tradeRecord.getId());
        vo.setItemId(tradeRecord.getItemId());
        vo.setBuyerId(tradeRecord.getBuyerId());
        vo.setSellerId(tradeRecord.getSellerId());
        vo.setStatus(tradeRecord.getStatus() != null ? tradeRecord.getStatus().name() : null);
        vo.setStatusText(getStatusText(tradeRecord.getStatus()));
        vo.setCreateTime(tradeRecord.getCreateTime() != null ? tradeRecord.getCreateTime().format(FMT) : null);
        vo.setUpdateTime(tradeRecord.getUpdateTime() != null ? tradeRecord.getUpdateTime().format(FMT) : null);
        vo.setIsSeller(Objects.equals(currentUserId, tradeRecord.getSellerId()));
        vo.setOtherUserId(Objects.equals(currentUserId, tradeRecord.getSellerId()) ? tradeRecord.getBuyerId() : tradeRecord.getSellerId());

        if (tradeRecord.getItem() != null) {
            vo.setItemTitle(tradeRecord.getItem().getTitle());
            vo.setItemPrice(tradeRecord.getItem().getPrice());
        }
        if (tradeRecord.getBuyer() != null) {
            vo.setBuyerName(tradeRecord.getBuyer().getUsername());
        }
        if (tradeRecord.getSeller() != null) {
            vo.setSellerName(tradeRecord.getSeller().getUsername());
        }
        if (tradeRecord.getBuyer() != null && Objects.equals(vo.getOtherUserId(), tradeRecord.getBuyerId())) {
            vo.setOtherUsername(tradeRecord.getBuyer().getUsername());
            vo.setOtherAvatar(tradeRecord.getBuyer().getAvatar());
        } else if (tradeRecord.getSeller() != null && Objects.equals(vo.getOtherUserId(), tradeRecord.getSellerId())) {
            vo.setOtherUsername(tradeRecord.getSeller().getUsername());
            vo.setOtherAvatar(tradeRecord.getSeller().getAvatar());
        }
        vo.setContactExchangeStatus(tradeRecord.getContactExchangeStatus());
        vo.setContactExchangeRequesterId(tradeRecord.getContactExchangeRequesterId());
        vo.setContactExchange(buildContactExchangeVO(tradeRecord, currentUserId));
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
        boolean trading = tradeRecord.getStatus() == TradeStatus.TRADING;
        boolean exchanged = CONTACT_EXCHANGE_EXCHANGED.equals(tradeRecord.getContactExchangeStatus());

        ChatDTO.ContactExchangeVO vo = new ChatDTO.ContactExchangeVO();
        vo.setStatus(tradeRecord.getContactExchangeStatus() == null ? CONTACT_EXCHANGE_NONE : tradeRecord.getContactExchangeStatus());
        vo.setCurrentUserAgreed(currentAgreed);
        vo.setOtherUserAgreed(otherAgreed);
        vo.setCanAgree(trading && !exchanged && !currentAgreed && tradeRecord.getContactExchangeRequesterId() != null);
        vo.setCanDecline(trading && !exchanged && !currentAgreed && tradeRecord.getContactExchangeRequesterId() != null);
        vo.setRequesterId(tradeRecord.getContactExchangeRequesterId());
        return vo;
    }

    private void normalizeContactExchangeState(TradeRecord tradeRecord) {
        if (CONTACT_EXCHANGE_EXCHANGED.equals(tradeRecord.getContactExchangeStatus())) {
            tradeRecord.setBuyerContactAgreed(true);
            tradeRecord.setSellerContactAgreed(true);
            return;
        }
        if ("PENDING".equals(tradeRecord.getContactExchangeStatus())) {
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

    private void cancelOtherActiveTrades(TradeRecord completedTrade) {
        List<TradeRecord> activeTrades = tradeRecordRepository.findByItemIdAndStatusIn(completedTrade.getItemId(), ACTIVE_STATUSES);
        for (TradeRecord activeTrade : activeTrades) {
            if (Objects.equals(activeTrade.getId(), completedTrade.getId())) {
                continue;
            }
            activeTrade.setStatus(TradeStatus.CANCELLED);
            tradeRecordRepository.save(activeTrade);
            createTradeMessage(activeTrade, completedTrade.getSellerId(), "本次交易已取消");
        }
    }

    private String getStatusText(TradeStatus status) {
        if (status == null) return "";
        switch (status) {
            case WAIT_CONFIRM: return "待确认";
            case TRADING: return "交易中";
            case COMPLETED: return "已完成";
            case CANCELLED: return "已取消";
            default: return "";
        }
    }

    private void createTradeMessage(TradeRecord tradeRecord, Long actorId, String content) {
        chatService.createOrGetSession(tradeRecord.getItemId(), tradeRecord.getBuyerId());
        chatService.createTradeSystemMessage(tradeRecord.getItemId(), tradeRecord.getBuyerId(), actorId, content);
    }

    private void createLostFoundAppMessage(TradeRecord tradeRecord, Long userId, String eventType, String title, String content) {
        if (tradeRecord == null || userId == null) return;
        AppMessageDTO.CreateCommand command = new AppMessageDTO.CreateCommand();
        command.setUserId(userId);
        command.setModuleType("LOST_FOUND");
        command.setEventType(eventType);
        command.setTitle(title);
        command.setContent(content);
        command.setTargetPage("/subpackage_lostfound/lostfoundChat/lostfoundChat");
        command.setTargetParams(buildTargetParams(tradeRecord));
        command.setSourceType("TRADE_RECORD");
        command.setSourceId(tradeRecord.getId());
        appMessageService.createIfAbsent(command);
    }

    private String buildTargetParams(TradeRecord tradeRecord) {
        Long sessionId = sessionRepository.findByItemIdAndBuyerId(tradeRecord.getItemId(), tradeRecord.getBuyerId())
                .map(session -> session.getId())
                .orElse(null);
        return "{\"itemId\":" + tradeRecord.getItemId() +
                ",\"sessionId\":" + (sessionId == null ? "null" : sessionId) +
                ",\"tradeId\":" + tradeRecord.getId() + "}";
    }
}
