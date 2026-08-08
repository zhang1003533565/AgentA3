package com.example.appbackend.controller;

import com.example.appbackend.dto.DocumentConvertDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.DocumentConvertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/ai/convert")
@Tag(name = "AI 文档格式转换", description = "AI 智能文档格式转换助手接口")
public class DocumentConvertController {

    private final DocumentConvertService documentConvertService;

    public DocumentConvertController(DocumentConvertService documentConvertService) {
        this.documentConvertService = documentConvertService;
    }

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建文档转换任务", description = "上传源文件并指定转换类型，异步执行转换")
    public Result<DocumentConvertDTO.TaskAccepted> createTask(
            @RequestParam("file") MultipartFile file,
            @RequestParam("convertType") String convertType,
            HttpServletRequest request) {
        return Result.success(documentConvertService.createTask(
                file, convertType, requireUserId(request), request.getHeader("Authorization")));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询转换任务状态")
    public Result<DocumentConvertDTO.TaskView> getTask(
            @PathVariable String taskId,
            HttpServletRequest request) {
        return Result.success(documentConvertService.getTask(taskId, requireUserId(request)));
    }

    @GetMapping("/tasks")
    @Operation(summary = "查询转换历史记录")
    public Result<PageResponse<DocumentConvertDTO.TaskSummary>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String convertType,
            HttpServletRequest request) {
        return Result.success(documentConvertService.getHistory(requireUserId(request), convertType, page, size));
    }

    @PostMapping("/tasks/batch-delete")
    @Operation(summary = "批量删除转换任务", description = "仅允许删除已完成的转换记录（SUCCEEDED/FAILED）")
    public Result<Void> batchDelete(
            @Valid @RequestBody DocumentConvertDTO.BatchDeleteRequest request,
            HttpServletRequest httpRequest) {
        documentConvertService.batchDelete(request.getTaskIds(), requireUserId(httpRequest));
        return Result.success("删除成功", null);
    }

    @GetMapping("/tasks/{taskId}/download")
    @Operation(summary = "下载转换结果文件")
    public ResponseEntity<byte[]> download(
            @PathVariable String taskId,
            HttpServletRequest request) {
        Long userId = requireUserId(request);
        DocumentConvertDTO.TaskView taskView = documentConvertService.getTask(taskId, userId);
        byte[] bytes = documentConvertService.downloadResult(taskId, userId);
        String filename = StringUtils.hasText(taskView.getResultFileName())
                ? taskView.getResultFileName()
                : taskId;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }

    private Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute("userId");
        if (!(raw instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
