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
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        requireAdmin(request);
        if (sortBy != null && !sortBy.isEmpty()) {
            return Result.success(datasetService.listDocumentsSorted(datasetId, keyword, sortBy, current, size));
        }
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

    @PatchMapping("/documents/{id}/processing/{action}")
    @Operation(summary = "暂停或恢复文档索引")
    public Result<Void> processDocumentAction(
            @PathVariable Long id,
            @PathVariable String action,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.processDocumentAction(id, action);
        return Result.success("操作成功", (Void) null);
    }

    @PostMapping("/documents/{id}/rename")
    @Operation(summary = "重命名文档")
    public Result<DatasetDTO.DocumentVO> renameDocument(
            @PathVariable Long id,
            @Valid @RequestBody DatasetDTO.RenameRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.renameDocument(id, req));
    }

    @PatchMapping("/documents/{id}/archive")
    @Operation(summary = "归档文档")
    public Result<Void> archiveDocument(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.archiveDocument(id);
        return Result.success("文档已归档", (Void) null);
    }

    @PatchMapping("/documents/{id}/unarchive")
    @Operation(summary = "取消归档")
    public Result<Void> unarchiveDocument(
            @PathVariable Long id,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.unarchiveDocument(id);
        return Result.success("文档已取消归档", (Void) null);
    }

    @PostMapping("/{datasetId}/retry")
    @Operation(summary = "重试失败文档")
    public Result<Void> retryFailedDocuments(
            @PathVariable Long datasetId,
            @RequestBody DatasetDTO.RetryRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.retryFailedDocuments(datasetId, req);
        return Result.success("重试已触发", (Void) null);
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

    @PostMapping("/documents/{documentId}/segment")
    @Operation(summary = "手动创建分段")
    public Result<DatasetDTO.SegmentVO> createSegment(
            @PathVariable Long documentId,
            @Valid @RequestBody DatasetDTO.CreateSegmentRequest req,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(datasetService.createSegment(documentId, req));
    }

    @PatchMapping("/documents/{documentId}/segment/{action}")
    @Operation(summary = "批量启用/禁用分段")
    public Result<Void> batchToggleSegments(
            @PathVariable Long documentId,
            @PathVariable String action,
            @RequestParam List<Long> segmentIds,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.batchToggleSegments(documentId, action, segmentIds);
        return Result.success("操作成功", (Void) null);
    }

    @DeleteMapping("/documents/{documentId}/segments")
    @Operation(summary = "批量删除分段")
    public Result<Void> batchDeleteSegments(
            @PathVariable Long documentId,
            @RequestParam List<Long> segmentIds,
            HttpServletRequest request) {
        requireAdmin(request);
        datasetService.batchDeleteSegments(documentId, segmentIds);
        return Result.success("分段已删除", (Void) null);
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
