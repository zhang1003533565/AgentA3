package com.example.appbackend.controller;

import com.example.appbackend.dto.LangfuseConfigDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.LangfuseConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/observability")
public class LangfuseConfigController {
    private final LangfuseConfigService langfuseConfigService;

    public LangfuseConfigController(LangfuseConfigService langfuseConfigService) {
        this.langfuseConfigService = langfuseConfigService;
    }

    @GetMapping
    public Result<LangfuseConfigDTO.ConfigVO> get(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(langfuseConfigService.getConfig());
    }

    @PutMapping
    public Result<LangfuseConfigDTO.ConfigVO> update(@Valid @RequestBody LangfuseConfigDTO.UpdateRequest request,
                                                      HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return Result.success(langfuseConfigService.updateConfig(request));
    }

    @PostMapping("/test")
    public Result<LangfuseConfigDTO.TestResultVO> test(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(langfuseConfigService.testConfig());
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        }
    }
}
