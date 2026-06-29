package com.example.appbackend.controller;

import com.example.appbackend.dto.MaxKbKnowledgeDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MaxKbKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/maxkb")
@Tag(name = "MaxKB 知识库", description = "管理多个 MaxKB 账号，并按账号权限代理 Knowledge OpenAPI")
public class MaxKbKnowledgeController {
    private static final String ROLE_ADMIN = "ADMIN";

    private final MaxKbKnowledgeService maxKbKnowledgeService;

    public MaxKbKnowledgeController(MaxKbKnowledgeService maxKbKnowledgeService) {
        this.maxKbKnowledgeService = maxKbKnowledgeService;
    }

    @GetMapping("/environments")
    @Operation(summary = "MaxKB 环境选项", description = "供管理后台选择本地、测试、线上或自定义环境")
    public Result<List<MaxKbKnowledgeDTO.EnvironmentOption>> listEnvironments(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.listEnvironmentOptions());
    }

    @GetMapping("/accounts")
    @Operation(summary = "MaxKB 账号列表", description = "管理员权限；不同账号可拥有不同知识库权限")
    public Result<PageResponse<MaxKbKnowledgeDTO.AccountVO>> listAccounts(
            @Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "环境：local/test/prod/custom") @RequestParam(required = false) String environment,
            @Parameter(description = "状态：1-启用 0-禁用") @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.listAccounts(current, size, keyword, environment, status));
    }

    @PostMapping("/accounts")
    @Operation(summary = "新增 MaxKB 账号", description = "保存账号名称、MaxKB 地址、OpenAPI Key 和工作空间 ID")
    public Result<MaxKbKnowledgeDTO.AccountVO> createAccount(
            @Valid @RequestBody MaxKbKnowledgeDTO.AccountCreateRequest body,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success("创建成功", maxKbKnowledgeService.createAccount(body));
    }

    @PutMapping("/accounts/{accountId}")
    @Operation(summary = "编辑 MaxKB 账号", description = "apiKey 为空时保留原密钥")
    public Result<MaxKbKnowledgeDTO.AccountVO> updateAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody MaxKbKnowledgeDTO.AccountUpdateRequest body,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success("更新成功", maxKbKnowledgeService.updateAccount(accountId, body));
    }

    @DeleteMapping("/accounts/{accountId}")
    @Operation(summary = "删除 MaxKB 账号")
    public Result<Void> deleteAccount(@PathVariable Long accountId, HttpServletRequest request) {
        requireAdmin(request);
        maxKbKnowledgeService.deleteAccount(accountId);
        return Result.success("删除成功", (Void) null);
    }

    @PutMapping("/accounts/{accountId}/status")
    @Operation(summary = "启用或禁用 MaxKB 账号")
    public Result<MaxKbKnowledgeDTO.AccountVO> updateAccountStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody MaxKbKnowledgeDTO.AccountStatusRequest body,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success("状态更新成功", maxKbKnowledgeService.updateAccountStatus(accountId, body.getStatus()));
    }

    @PostMapping("/accounts/{accountId}/test")
    @Operation(summary = "测试 MaxKB 账号连接", description = "使用该账号的 OpenAPI Key 拉取一条知识库记录")
    public Result<Object> test(@PathVariable Long accountId, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.testConnection(accountId));
    }

    @GetMapping("/accounts/{accountId}/docs")
    @Operation(summary = "获取 MaxKB Knowledge OpenAPI 文档")
    public Result<Object> docs(@PathVariable Long accountId, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.docs(accountId));
    }

    @GetMapping("/accounts/{accountId}/knowledges")
    @Operation(summary = "按账号权限分页获取知识库")
    public Result<Object> listKnowledges(
            @PathVariable Long accountId,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.listKnowledges(accountId, queryParams));
    }

    @GetMapping("/accounts/{accountId}/knowledges/{knowledgeId}")
    @Operation(summary = "按账号权限获取知识库详情")
    public Result<Object> getKnowledge(
            @PathVariable Long accountId,
            @PathVariable String knowledgeId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.getKnowledge(accountId, knowledgeId));
    }

    @GetMapping("/accounts/{accountId}/knowledges/{knowledgeId}/documents")
    @Operation(summary = "按账号权限分页获取知识库文档")
    public Result<Object> listDocuments(
            @PathVariable Long accountId,
            @PathVariable String knowledgeId,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.listDocuments(accountId, knowledgeId, queryParams));
    }

    @PostMapping(value = "/accounts/{accountId}/knowledges/{knowledgeId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "按账号权限上传文档到知识库")
    public Result<Object> uploadDocuments(
            @PathVariable Long accountId,
            @PathVariable String knowledgeId,
            @RequestParam("file") List<MultipartFile> files,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) List<String> patterns,
            @RequestParam(required = false, name = "with_filter") Boolean withFilter,
            @RequestParam(required = false, name = "split_strategy") String splitStrategy,
            @RequestParam(required = false, name = "model_id") String modelId,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.uploadDocuments(
                accountId, knowledgeId, files, limit, patterns, withFilter, splitStrategy, modelId
        ));
    }

    @GetMapping("/accounts/{accountId}/knowledges/{knowledgeId}/documents/{documentId}/paragraphs")
    @Operation(summary = "按账号权限分页获取文档分段")
    public Result<Object> listParagraphs(
            @PathVariable Long accountId,
            @PathVariable String knowledgeId,
            @PathVariable String documentId,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.listParagraphs(accountId, knowledgeId, documentId, queryParams));
    }

    @GetMapping("/accounts/{accountId}/assets")
    @Operation(summary = "代理 MaxKB 文档图片资源", description = "用于渲染分段内容中的 /oss/file 图片")
    public ResponseEntity<byte[]> proxyAsset(
            @PathVariable Long accountId,
            @RequestParam String path,
            HttpServletRequest request) {
        requireAdmin(request);
        return maxKbKnowledgeService.proxyAsset(accountId, path);
    }

    @PostMapping("/accounts/{accountId}/hit-test")
    @Operation(summary = "按账号权限做知识库召回测试")
    public Result<Object> hitTest(
            @PathVariable Long accountId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(maxKbKnowledgeService.hitTest(accountId, body));
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员可执行");
        }
    }
}
