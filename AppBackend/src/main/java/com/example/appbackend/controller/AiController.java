package com.example.appbackend.controller;

import com.example.appbackend.dto.AiWriteDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 写作", description = "AI 写作接口")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/write")
    @Operation(summary = "智能写作")
    public Result<AiWriteDTO.WriteResponse> write(
            @Valid @RequestBody AiWriteDTO.WriteRequest request,
            HttpServletRequest httpRequest) {
        Object userId = httpRequest.getAttribute("userId");
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return Result.success(aiService.write(request));
    }
}
