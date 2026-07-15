package com.example.appbackend.service;

import com.example.appbackend.dto.ChatDTO;
import com.example.appbackend.dto.PageResponse;

public interface TradeService {

    ChatDTO.TradeRecordVO createTradeRecord(ChatDTO.CreateTradeRecordRequest req, Long currentUserId);

    ChatDTO.TradeRecordVO reserveTrade(Long itemId, Long currentUserId);

    ChatDTO.TradeRecordVO confirmTrade(Long id, Long currentUserId);

    ChatDTO.TradeRecordVO completeTrade(Long id, Long currentUserId);

    ChatDTO.TradeRecordVO cancelTrade(Long id, Long currentUserId);

    ChatDTO.TradeRecordVO getTradeRecord(Long id, Long currentUserId);

    PageResponse<ChatDTO.TradeRecordVO> getTradeList(Long currentUserId, Integer current, Integer size);
}
