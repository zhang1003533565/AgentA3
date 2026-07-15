package com.example.appbackend.controller;

import com.example.appbackend.dto.ChatDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trade")
@Tag(name = "校园市集交易", description = "交易记录基础接口")
public class TradeController {

    @Autowired private TradeService tradeService;

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return (Long) userId;
    }

    @PostMapping("/record")
    @Operation(summary = "创建交易记录", description = "兼容基础创建入口：为当前用户拍下商品，创建 WAIT_CONFIRM 交易并锁定商品为交易中")
    public Result<ChatDTO.TradeRecordVO> createTradeRecord(
            @Valid @RequestBody ChatDTO.CreateTradeRecordRequest req,
            HttpServletRequest httpRequest) {
        return Result.success("交易记录已创建", tradeService.createTradeRecord(req, getUserId(httpRequest)));
    }

    @PostMapping("/record/reserve/{itemId}")
    @Operation(summary = "拍下商品", description = "创建 WAIT_CONFIRM 交易记录，并将商品状态从 2-在售 改为 5-交易中")
    public Result<ChatDTO.TradeRecordVO> reserveTrade(
            @PathVariable Long itemId,
            HttpServletRequest httpRequest) {
        return Result.success("已拍下", tradeService.reserveTrade(itemId, getUserId(httpRequest)));
    }

    @PostMapping("/record/{id}/confirm")
    @Operation(summary = "卖家确认交易", description = "交易状态从 WAIT_CONFIRM 改为 TRADING，商品保持 5-交易中")
    public Result<ChatDTO.TradeRecordVO> confirmTrade(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return Result.success("交易已确认", tradeService.confirmTrade(id, getUserId(httpRequest)));
    }

    @PostMapping("/record/{id}/complete")
    @Operation(summary = "完成交易", description = "交易状态从 TRADING 改为 COMPLETED，商品状态从 5-交易中 改为 3-已售出")
    public Result<ChatDTO.TradeRecordVO> completeTrade(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return Result.success("交易已完成", tradeService.completeTrade(id, getUserId(httpRequest)));
    }

    @PostMapping("/record/{id}/cancel")
    @Operation(summary = "取消交易", description = "交易状态从 WAIT_CONFIRM/TRADING 改为 CANCELLED，商品状态从 5-交易中 恢复为 2-在售")
    public Result<ChatDTO.TradeRecordVO> cancelTrade(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return Result.success("交易已取消", tradeService.cancelTrade(id, getUserId(httpRequest)));
    }

    @GetMapping("/record/list")
    @Operation(summary = "交易记录列表", description = "获取当前用户参与的交易记录")
    public Result<PageResponse<ChatDTO.TradeRecordVO>> getTradeList(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest httpRequest) {
        return Result.success(tradeService.getTradeList(getUserId(httpRequest), current, size));
    }

    @GetMapping("/record/{id}")
    @Operation(summary = "交易记录详情", description = "根据交易记录ID获取详情")
    public Result<ChatDTO.TradeRecordVO> getTradeRecord(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        return Result.success(tradeService.getTradeRecord(id, getUserId(httpRequest)));
    }
}
