package com.example.appbackend.service.impl;

import com.example.appbackend.dto.ChatDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.SecondhandItem;
import com.example.appbackend.entity.TradeRecord;
import com.example.appbackend.entity.TradeRecord.TradeStatus;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SecondhandItemRepository;
import com.example.appbackend.repository.TradeRecordRepository;
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
    @Autowired private ChatService chatService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<TradeStatus> ACTIVE_STATUSES = Arrays.asList(TradeStatus.WAIT_CONFIRM, TradeStatus.TRADING);
    private static final int ITEM_ON_SALE = 2;
    private static final int ITEM_SOLD = 3;
    private static final int ITEM_TRADING = 5;

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
            throw new BusinessException(400, "不能拍下自己发布的商品");
        }
        if (!Integer.valueOf(ITEM_ON_SALE).equals(item.getStatus())) {
            throw new BusinessException(400, "商品当前不可拍下");
        }
        if (tradeRecordRepository.findByItemIdAndStatusIn(itemId, ACTIVE_STATUSES).isPresent()) {
            throw new BusinessException(400, "商品已有有效交易");
        }

        TradeRecord tradeRecord = new TradeRecord();
        tradeRecord.setItemId(itemId);
        tradeRecord.setBuyerId(currentUserId);
        tradeRecord.setSellerId(item.getUserId());
        tradeRecord.setStatus(TradeStatus.WAIT_CONFIRM);
        tradeRecord = tradeRecordRepository.save(tradeRecord);

        int updated = itemRepository.updateStatusIfCurrent(itemId, ITEM_ON_SALE, ITEM_TRADING);
        if (updated != 1) {
            throw new BusinessException(400, "商品当前不可拍下");
        }
        createTradeMessage(tradeRecord, currentUserId, "买家已拍下商品，请确认交易");
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
        ensureItemStatus(tradeRecord.getItemId(), ITEM_TRADING, "商品状态异常，无法确认交易");
        tradeRecord.setStatus(TradeStatus.TRADING);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "卖家已确认交易");
        return toVO(tradeRecord, currentUserId);
    }

    @Override
    public ChatDTO.TradeRecordVO completeTrade(Long id, Long currentUserId) {
        TradeRecord tradeRecord = tradeRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "交易记录不存在"));
        checkParticipant(tradeRecord, currentUserId);
        if (tradeRecord.getStatus() != TradeStatus.TRADING) {
            throw new BusinessException(400, "只有交易中记录可以完成");
        }
        int updated = itemRepository.updateStatusIfCurrent(tradeRecord.getItemId(), ITEM_TRADING, ITEM_SOLD);
        if (updated != 1) {
            throw new BusinessException(400, "商品状态异常，无法完成交易");
        }
        tradeRecord.setStatus(TradeStatus.COMPLETED);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "交易已完成");
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
        int updated = itemRepository.updateStatusIfCurrent(tradeRecord.getItemId(), ITEM_TRADING, ITEM_ON_SALE);
        if (updated != 1) {
            throw new BusinessException(400, "商品状态异常，无法取消交易");
        }
        tradeRecord.setStatus(TradeStatus.CANCELLED);
        tradeRecord = tradeRecordRepository.save(tradeRecord);
        createTradeMessage(tradeRecord, currentUserId, "交易已取消");
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
        if (tradeRecord.getBuyer() != null && Objects.equals(vo.getOtherUserId(), tradeRecord.getBuyerId())) {
            vo.setOtherUsername(tradeRecord.getBuyer().getUsername());
            vo.setOtherAvatar(tradeRecord.getBuyer().getAvatar());
        } else if (tradeRecord.getSeller() != null && Objects.equals(vo.getOtherUserId(), tradeRecord.getSellerId())) {
            vo.setOtherUsername(tradeRecord.getSeller().getUsername());
            vo.setOtherAvatar(tradeRecord.getSeller().getAvatar());
        }
        return vo;
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
}
