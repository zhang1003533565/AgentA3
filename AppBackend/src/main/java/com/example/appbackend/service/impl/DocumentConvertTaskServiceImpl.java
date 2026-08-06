package com.example.appbackend.service.impl;

import com.example.appbackend.dto.DocumentConvertDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.DocumentConvertTask;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.DocumentConvertTaskRepository;
import com.example.appbackend.service.DocumentConvertService;
import com.example.appbackend.service.impl.DocumentConvertServiceImpl.SourceFileInfo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 文档格式转换任务服务。
 * 负责任务创建、文件保存、校验、异步执行与状态流转。
 */
@Service
public class DocumentConvertTaskServiceImpl implements DocumentConvertService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            DocumentConvertTask.STATUS_QUEUED,
            DocumentConvertTask.STATUS_RUNNING
    );
    private static final int MAX_ACTIVE_TASKS_PER_USER = 3;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final DocumentConvertTaskRepository taskRepository;
    private final DocumentConvertServiceImpl documentConvertService;
    private final Executor taskExecutor;

    public DocumentConvertTaskServiceImpl(
            DocumentConvertTaskRepository taskRepository,
            DocumentConvertServiceImpl documentConvertService,
            @Qualifier("documentConvertExecutor") Executor taskExecutor) {
        this.taskRepository = taskRepository;
        this.documentConvertService = documentConvertService;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public DocumentConvertDTO.TaskAccepted createTask(
            MultipartFile file, String convertType, Long userId, String authorization) {
        requireUser(userId);
        validateRequest(file, convertType);
        if (taskRepository.countByUserIdAndStatusIn(userId, ACTIVE_STATUSES) >= MAX_ACTIVE_TASKS_PER_USER) {
            throw new BusinessException(429, "已有较多任务正在处理，请稍后再试");
        }

        final SourceFileInfo sourceInfo;
        try {
            sourceInfo = documentConvertService.saveSourceFile(file);
        } catch (IOException error) {
            throw new BusinessException(Result.ERROR_CODE, "源文件保存失败: " + error.getMessage());
        }

        DocumentConvertTask task = new DocumentConvertTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setConvertType(convertType);
        task.setStatus(DocumentConvertTask.STATUS_QUEUED);
        task.setProgress(0);
        task.setMessage("转换任务已排队");
        task.setSourceFileName(sourceInfo.fileName());
        task.setSourceFileUrl(sourceInfo.url());
        task.setSourceFileSize(sourceInfo.size());
        task.setSourceStorageKey(sourceInfo.storageKey());
        task = taskRepository.save(task);

        Long databaseId = task.getId();
        try {
            taskExecutor.execute(() -> runTask(databaseId, authorization));
        } catch (RejectedExecutionException | IllegalStateException error) {
            failTask(task, "转换任务队列暂时繁忙，请稍后重试");
            throw new BusinessException(503, "转换任务队列暂时繁忙，请稍后重试");
        }

        DocumentConvertDTO.TaskAccepted accepted = new DocumentConvertDTO.TaskAccepted();
        accepted.setTaskId(task.getTaskId());
        accepted.setStatus(task.getStatus());
        accepted.setProgress(task.getProgress());
        accepted.setMessage(task.getMessage());
        return accepted;
    }

    @Override
    public DocumentConvertDTO.TaskView getTask(String taskId, Long userId) {
        requireUser(userId);
        return toView(taskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "转换任务不存在")));
    }

    @Override
    public PageResponse<DocumentConvertDTO.TaskSummary> getHistory(
            Long userId, String convertType, int page, int size) {
        requireUser(userId);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        List<DocumentConvertTask> tasks;
        long total;
        if (StringUtils.hasText(convertType)) {
            Page<DocumentConvertTask> result = taskRepository.findByUserIdAndConvertType(
                    userId, convertType, PageRequest.of(safePage - 1, safeSize));
            tasks = result.getContent();
            total = result.getTotalElements();
        } else {
            List<DocumentConvertTask> all = taskRepository.findByUserIdOrderByCreateTimeDesc(userId);
            total = all.size();
            tasks = all.stream()
                    .skip((long) (safePage - 1) * safeSize)
                    .limit(safeSize)
                    .toList();
        }

        PageResponse<DocumentConvertDTO.TaskSummary> response = new PageResponse<>();
        response.setRecords(tasks.stream().map(this::toSummary).toList());
        response.setTotal(total);
        response.setPage(safePage);
        response.setSize(safeSize);
        return response;
    }

    @Override
    public byte[] downloadResult(String taskId, Long userId) {
        requireUser(userId);
        DocumentConvertTask task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "转换任务不存在"));
        if (!DocumentConvertTask.STATUS_SUCCEEDED.equals(task.getStatus())) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "任务尚未完成");
        }
        if (!StringUtils.hasText(task.getResultStorageKey())) {
            throw new BusinessException(Result.ERROR_CODE, "结果文件不存在");
        }
        try {
            return documentConvertService.loadFile(task.getResultStorageKey());
        } catch (IOException error) {
            throw new BusinessException(Result.ERROR_CODE, "结果文件读取失败");
        }
    }

    @Override
    public void batchDelete(List<String> taskIds, Long userId) {
        requireUser(userId);
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请选择要删除的记录");
        }

        List<DocumentConvertTask> removableTasks = new ArrayList<>();
        for (String taskId : taskIds) {
            // 按 taskId + userId 查询，非本人或不存在直接跳过，不暴露任务是否存在
            DocumentConvertTask task = taskRepository.findByTaskIdAndUserId(taskId, userId).orElse(null);
            if (task == null) {
                continue;
            }
            if (ACTIVE_STATUSES.contains(task.getStatus())) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, "存在正在处理中的任务，无法删除");
            }
            removableTasks.add(task);
        }

        for (DocumentConvertTask task : removableTasks) {
            // 先清理源文件与结果文件，再删除数据库记录
            documentConvertService.deleteFile(task.getSourceStorageKey());
            documentConvertService.deleteFile(task.getResultStorageKey());
            taskRepository.delete(task);
        }
    }

    private void runTask(Long databaseId, String authorization) {
        DocumentConvertTask task = taskRepository.findById(databaseId).orElse(null);
        if (task == null) {
            return;
        }
        try {
            task.setStatus(DocumentConvertTask.STATUS_RUNNING);
            task.setProgress(10);
            task.setMessage("正在读取源文件");
            task.setStartTime(LocalDateTime.now());
            taskRepository.save(task);

            // 转换核心（当前为占位方法，尚未接入 Python；不生成假成功结果）
            documentConvertService.executeConvertTask(task, authorization);

            task.setStatus(DocumentConvertTask.STATUS_SUCCEEDED);
            task.setProgress(100);
            task.setMessage("转换完成");
            task.setCompleteTime(LocalDateTime.now());
            taskRepository.save(task);
        } catch (Exception error) {
            DocumentConvertTask latest = taskRepository.findById(databaseId).orElse(task);
            failTask(latest, safeError(error));
        }
    }

    private void validateRequest(MultipartFile file, String convertType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "请选择要转换的文件");
        }
        if (!documentConvertService.isSupportedConvertType(convertType)) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "不支持的转换类型: " + convertType);
        }
        String expectedExtension = documentConvertService.expectedExtension(convertType);
        if (!expectedExtension.equals(extensionOf(file.getOriginalFilename()))) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "仅支持 " + expectedExtension + " 文件");
        }
        if (file.getSize() > DocumentConvertServiceImpl.MAX_FILE_BYTES) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "文件大小不能超过 25MB");
        }
    }

    private void failTask(DocumentConvertTask task, String message) {
        task.setStatus(DocumentConvertTask.STATUS_FAILED);
        task.setProgress(100);
        task.setMessage("转换任务处理失败");
        task.setErrorMessage(truncate(message, MAX_ERROR_LENGTH));
        task.setCompleteTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
    }

    private String safeError(Exception error) {
        String message = error.getMessage();
        if (!StringUtils.hasText(message)) {
            message = error.getClass().getSimpleName();
        }
        return truncate(message, MAX_ERROR_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private DocumentConvertDTO.TaskView toView(DocumentConvertTask task) {
        DocumentConvertDTO.TaskView view = new DocumentConvertDTO.TaskView();
        view.setTaskId(task.getTaskId());
        view.setConvertType(task.getConvertType());
        view.setStatus(task.getStatus());
        view.setProgress(task.getProgress());
        view.setMessage(task.getMessage());
        view.setErrorMessage(task.getErrorMessage());
        view.setSourceFileName(task.getSourceFileName());
        view.setSourceFileSize(task.getSourceFileSize());
        view.setResultFileName(task.getResultFileName());
        view.setResultFileSize(task.getResultFileSize());
        view.setCreateTime(task.getCreateTime());
        view.setStartTime(task.getStartTime());
        view.setCompleteTime(task.getCompleteTime());
        return view;
    }

    private DocumentConvertDTO.TaskSummary toSummary(DocumentConvertTask task) {
        DocumentConvertDTO.TaskSummary summary = new DocumentConvertDTO.TaskSummary();
        summary.setTaskId(task.getTaskId());
        summary.setConvertType(task.getConvertType());
        summary.setStatus(task.getStatus());
        summary.setProgress(task.getProgress());
        summary.setSourceFileName(task.getSourceFileName());
        summary.setResultFileName(task.getResultFileName());
        summary.setCreateTime(task.getCreateTime());
        summary.setCompleteTime(task.getCompleteTime());
        return summary;
    }
}
