package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.RealtimeTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {

    private final RealtimeTicketService ticketService;

    public RealtimeController(RealtimeTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/ticket")
    public Result<Map<String, String>> issueTicket(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long userId)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return Result.success(Map.of("ticket", ticketService.issue(userId)));
    }
}
