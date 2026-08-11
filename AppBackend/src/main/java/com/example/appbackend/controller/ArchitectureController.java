package com.example.appbackend.controller;

import com.example.appbackend.dto.ArchitectureDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ArchitectureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 架构图生成接口。
 *
 * 对外暴露三个端点：
 *   POST /api/ai/architecture/generate  生成架构图
 *   GET  /api/ai/architecture/history   查询历史记录
 *   GET  /api/ai/architecture/{id}      查询架构图详情
 *
 * 鉴权依赖 JWT 过滤器写入 request 的 userId 属性，与 AppAiLeaderController 保持一致。
 */
@RestController
@RequestMapping("/api/ai/architecture")
@Tag(name = "AI 架构图生成", description = "根据需求描述生成软件系统架构图")
public class ArchitectureController {

    private final ArchitectureService architectureService;

    public ArchitectureController(ArchitectureService architectureService) {
        this.architectureService = architectureService;
    }

    @PostMapping("/generate")
    @Operation(summary = "生成架构图", description = "调用 AI 模型生成结构化架构数据并保存记录")
    public Result<ArchitectureDTO.GenerateResponse> generate(@RequestBody ArchitectureDTO.GenerateRequest request,
                                                              HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        String authorization = httpRequest.getHeader("Authorization");
        ArchitectureDTO.GenerateResponse data = architectureService.generate(request, userId, authorization);
        return Result.success(data);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档解析", description = "上传 pdf/word/ppt/md 文档，解析为文本供 AI 生成架构图使用")
    public Result<ArchitectureDTO.UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                          HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        return Result.success(architectureService.uploadAndParse(userId, file));
    }

    @GetMapping("/history")
    @Operation(summary = "查询历史记录", description = "分页查询当前用户的架构图生成记录")
    public Result<PageResponse<ArchitectureDTO.HistoryItem>> history(@RequestParam(value = "page", required = false) Integer page,
                                                                      @RequestParam(value = "size", required = false) Integer size,
                                                                      HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        PageResponse<ArchitectureDTO.HistoryItem> data = architectureService.history(userId, page, size);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询架构图详情", description = "按记录ID查询完整架构JSON，仅限本人记录")
    public Result<ArchitectureDTO.GenerateResponse> detail(@PathVariable("id") Long id,
                                                           HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        ArchitectureDTO.GenerateResponse data = architectureService.detail(id, userId);
        return Result.success(data);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除架构图记录", description = "按记录ID删除当前用户自己的架构图历史记录")
    public Result<Void> delete(@PathVariable("id") Long id,
                               HttpServletRequest httpRequest) {
        Long userId = currentUserId(httpRequest);
        architectureService.delete(id, userId);
        return Result.success();
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return (Long) userId;
    }
}
