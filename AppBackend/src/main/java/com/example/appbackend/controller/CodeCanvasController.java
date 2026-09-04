package com.example.appbackend.controller;

import com.example.appbackend.dto.CodeCanvasDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.CodeCanvasService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 代码画布：输入后端程序代码，AI 生成前端预览页面。
 * 纯新增控制器，不修改任何已有接口。
 */
@RestController
@RequestMapping("/api/ai/code-canvas")
public class CodeCanvasController {

    private final CodeCanvasService codeCanvasService;

    public CodeCanvasController(CodeCanvasService codeCanvasService) {
        this.codeCanvasService = codeCanvasService;
    }

    /**
     * 根据后端代码生成前端预览页面 HTML。
     */
    @PostMapping("/generate")
    public Result<CodeCanvasDTO.GenerateResponse> generate(@Valid @RequestBody CodeCanvasDTO.GenerateRequest body) {
        return Result.success(codeCanvasService.generate(body));
    }
}
