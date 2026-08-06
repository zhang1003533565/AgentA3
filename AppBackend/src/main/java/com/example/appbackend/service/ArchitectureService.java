package com.example.appbackend.service;

import com.example.appbackend.dto.ArchitectureDTO;
import com.example.appbackend.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI 架构图生成服务接口。
 */
public interface ArchitectureService {

    /**
     * 调用 AI 生成架构图并保存记录。
     */
    ArchitectureDTO.GenerateResponse generate(ArchitectureDTO.GenerateRequest request, Long userId, String authorization);

    /**
     * 上传文档并解析为文本（供 AI 生成架构图使用）。
     */
    ArchitectureDTO.UploadResponse uploadAndParse(Long userId, MultipartFile file);

    /**
     * 分页查询当前用户的历史记录。
     */
    PageResponse<ArchitectureDTO.HistoryItem> history(Long userId, Integer page, Integer size);

    /**
     * 查询单条架构图详情（仅限本人记录）。
     */
    ArchitectureDTO.GenerateResponse detail(Long id, Long userId);
}
