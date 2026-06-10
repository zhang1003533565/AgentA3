package com.example.appbackend.controller;

import com.example.appbackend.dto.DatasetDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasets")
@Tag(name = "知识库管理", description = "对标 Dify 知识库的完整 CRUD：知识库、文档、分段、子片段")
public class DatasetController {

    private static final String ROLE_ADMIN = "ADMIN";

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员可执行");
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) return id;
        if (userId instanceof Integer id) return id.longValue();
        if (userId instanceof String str) return Long.parseLong(str);
        throw new BusinessException(Result.UNAUTHORIZED_CODE, "无法获取用户 ID");
    }

    // ====== Dataset（知识库） ======

    @PostMapping
    @Operation(summary = "创建知识库")
    public Result<DatasetDTO.DatasetVO> createDataset(
            @Valid @RequestBody DatasetDTO.CreateRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.createDataset(req, getUserId(request)));
    }

    @GetMapping
    @Operation(summary = "知识库列表")
    public Result<PageResponse<DatasetDTO.DatasetListItem>> listDatasets(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.listDatasets(keyword, current, size));
    }

    @GetMapping("/{datasetId}")
    @Operation(summary = "知识库详情")
    public Result<DatasetDTO.DatasetVO> getDataset(
            @PathVariable Long datasetId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.getDataset(datasetId));
    }

    @PutMapping("/{datasetId}")
    @Operation(summary = "更新知识库")
    public Result<DatasetDTO.DatasetVO> updateDataset(
            @PathVariable Long datasetId,
            @Valid @RequestBody DatasetDTO.CreateRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.updateDataset(datasetId, req));
    }

    @DeleteMapping("/{datasetId}")
    @Operation(summary = "删除知识库")
    public Result<Void> deleteDataset(
            @PathVariable Long datasetId,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.deleteDataset(datasetId);
        return Result.success("知识库已删除", (Void) null);
    }

    // ====== ProcessRule（处理规则） ======

    @PostMapping("/{datasetId}/process-rules")
    @Operation(summary = "创建处理规则")
    public Result<DatasetDTO.ProcessRuleVO> createProcessRule(
            @PathVariable Long datasetId,
            @RequestBody DatasetDTO.ProcessRuleRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.createProcessRule(datasetId, req, getUserId(request)));
    }

    // ====== Document（文档） ======

    @PostMapping("/{datasetId}/documents")
    @Operation(summary = "创建文档并触发索引")
    public Result<DatasetDTO.DocumentVO> createDocument(
            @PathVariable Long datasetId,
            @Valid @RequestBody DatasetDTO.DocumentCreateRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.createDocument(datasetId, req, getUserId(request), request.getHeader("Authorization")));
    }

    @GetMapping("/{datasetId}/documents")
    @Operation(summary = "文档列表")
    public Result<PageResponse<DatasetDTO.DocumentListItem>> listDocuments(
            @PathVariable Long datasetId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.listDocuments(datasetId, keyword, current, size));
    }

    @GetMapping("/documents/{documentId}")
    @Operation(summary = "文档详情")
    public Result<DatasetDTO.DocumentVO> getDocument(
            @PathVariable Long documentId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.getDocument(documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(
            @PathVariable Long documentId,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.deleteDocument(documentId);
        return Result.success("文档已删除", (Void) null);
    }

    @PutMapping("/documents/{documentId}/toggle")
    @Operation(summary = "启用/禁用文档")
    public Result<Void> toggleDocument(
            @PathVariable Long documentId,
            @RequestParam boolean enabled,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.enableDocument(documentId, enabled);
        return Result.success("操作成功", (Void) null);
    }

    // ====== Segment（分段） ======

    @GetMapping("/documents/{documentId}/segments")
    @Operation(summary = "分段列表")
    public Result<PageResponse<DatasetDTO.SegmentListItem>> listSegments(
            @PathVariable Long documentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.listSegments(documentId, keyword, current, size));
    }

    @GetMapping("/segments/{segmentId}")
    @Operation(summary = "分段详情")
    public Result<DatasetDTO.SegmentVO> getSegment(
            @PathVariable Long segmentId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.getSegment(segmentId));
    }

    @PutMapping("/segments/{segmentId}")
    @Operation(summary = "更新分段")
    public Result<DatasetDTO.SegmentVO> updateSegment(
            @PathVariable Long segmentId,
            @RequestBody DatasetDTO.SegmentUpdateRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.updateSegment(segmentId, req));
    }

    @DeleteMapping("/segments/{segmentId}")
    @Operation(summary = "删除分段")
    public Result<Void> deleteSegment(
            @PathVariable Long segmentId,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.deleteSegment(segmentId);
        return Result.success("分段已删除", (Void) null);
    }

    @PutMapping("/segments/{segmentId}/toggle")
    @Operation(summary = "启用/禁用分段")
    public Result<Void> toggleSegment(
            @PathVariable Long segmentId,
            @RequestParam boolean enabled,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.toggleSegment(segmentId, enabled);
        return Result.success("操作成功", (Void) null);
    }

    // ====== ChildChunk（子片段） ======

    @GetMapping("/segments/{segmentId}/child-chunks")
    @Operation(summary = "子片段列表")
    public Result<List<DatasetDTO.ChildChunkVO>> listChildChunks(
            @PathVariable Long segmentId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.listChildChunks(segmentId));
    }
}
