package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.impl.PythonAiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/rag")
@Tag(name = "AI 智能体管理", description = "管理员代理 Python AI 服务的智能体、框架、文档转换和 Text-to-SQL 接口")
public class AiRagController {
    private static final String ROLE_ADMIN = "ADMIN";

    private final PythonAiProxyService pythonAiProxyService;

    public AiRagController(PythonAiProxyService pythonAiProxyService) {
        this.pythonAiProxyService = pythonAiProxyService;
    }

    @GetMapping("/capabilities")
    @Operation(summary = "AI 能力目录")
    public Result<Object> capabilities(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagCapabilities(adminAuthorization(request)));
    }

    @GetMapping("/framework")
    @Operation(summary = "AI 框架目录")
    public Result<Object> framework(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagFramework(adminAuthorization(request)));
    }

    @GetMapping("/agents")
    @Operation(summary = "AI 多智能体列表")
    public Result<Object> agents(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagAgents(adminAuthorization(request)));
    }

    @GetMapping("/model-providers")
    @Operation(summary = "AI 模型服务商目录")
    public Result<Object> modelProviders(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getModelProviders(adminAuthorization(request)));
    }

    @GetMapping("/agents/{agentName}")
    @Operation(summary = "AI 多智能体详情")
    public Result<Object> agent(@PathVariable String agentName, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagAgent(agentName, adminAuthorization(request)));
    }

    @PostMapping("/agents/{agentName}/example-input")
    @Operation(summary = "保存智能体示例输入")
    public Result<Object> saveAgentExampleInput(
            @PathVariable String agentName,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.updateRagAgentExampleInput(agentName, body, adminAuthorization(request)));
    }

    @GetMapping("/tool-cache/stats")
    @Operation(summary = "智能体工具缓存统计")
    public Result<Object> toolCacheStats(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getToolCacheStats(adminAuthorization(request)));
    }

    @DeleteMapping("/tool-cache")
    @Operation(summary = "清空智能体工具缓存")
    public Result<Object> clearToolCache(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.clearToolCache(adminAuthorization(request)));
    }

    @PostMapping("/query")
    @Operation(summary = "执行 AI 智能体查询")
    public Result<Object> query(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.queryRag(body, adminAuthorization(request)));
    }

    @PostMapping(value = "/pdf/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "PDF 转换为 Markdown 或 DOCX")
    public Result<Object> convertPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetFormat") String targetFormat,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.convertPdf(file, targetFormat, adminAuthorization(request)));
    }

    @PostMapping(value = "/tools/{toolName}/test-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "手动上传文件测试内容提取工具")
    public Result<Object> testFileContentTool(
            @PathVariable String toolName,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.testFileContentTool(toolName, file, adminAuthorization(request)));
    }

    @PostMapping(value = "/ppt/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "PPTX 转换为 DOCX")
    public Result<Object> convertPpt(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.convertPpt(file, adminAuthorization(request)));
    }

    @GetMapping("/text-to-sql/schema")
    @Operation(summary = "Text-to-SQL Schema")
    public Result<Object> textToSqlSchema(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getTextToSqlSchema(adminAuthorization(request)));
    }

    @PostMapping("/text-to-sql/execute")
    @Operation(summary = "执行 Text-to-SQL")
    public Result<Object> executeTextToSql(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.executeTextToSql(body, adminAuthorization(request)));
    }

    @GetMapping("/export")
    @Operation(summary = "下载 AI 生成的导出文件")
    public ResponseEntity<byte[]> exportDownload(
            @RequestParam String storageKey,
            @RequestParam String capability,
            @RequestParam(required = false) String filename,
            HttpServletRequest request) {
        adminAuthorization(request);
        PythonAiProxyService.GeneratedExportResponse exported = pythonAiProxyService
                .downloadGeneratedExport(storageKey, capability);
        byte[] bytes = exported == null || exported.bytes() == null ? new byte[0] : exported.bytes();
        String safeName = sanitizeExportFilename(filename == null || filename.isBlank() ? "ai-export" : filename);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(safeName, StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = exported.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : exported.contentType();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(bytes);
    }

    private String sanitizeExportFilename(String filename) {
        String sanitized = filename.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        return sanitized.isEmpty() ? "ai-export" : sanitized;
    }

    private String adminAuthorization(HttpServletRequest request) {
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员可执行");
        }
        return request.getHeader("Authorization");
    }
}
