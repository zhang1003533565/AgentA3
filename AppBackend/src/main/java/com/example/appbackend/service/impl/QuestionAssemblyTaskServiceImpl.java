package com.example.appbackend.service.impl;

import com.example.appbackend.dto.AppMessageDTO;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.PrivateCommitResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.TaskAccepted;
import com.example.appbackend.dto.QuestionAssemblyDTO.TaskView;
import com.example.appbackend.dto.QuestionGenerationDTO.ParsedMaterial;
import com.example.appbackend.entity.ExamQuestionAssemblyTask;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ExamQuestionAssemblyTaskRepository;
import com.example.appbackend.service.AppMessageService;
import com.example.appbackend.service.QuestionAssemblyService;
import com.example.appbackend.service.QuestionAssemblyTaskService;
import com.example.appbackend.service.QuestionGenerationMaterialParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class QuestionAssemblyTaskServiceImpl implements QuestionAssemblyTaskService {

    private static final List<String> ACTIVE_STATUSES = List.of(
            ExamQuestionAssemblyTask.STATUS_QUEUED,
            ExamQuestionAssemblyTask.STATUS_RUNNING);
    private static final int MAX_ACTIVE_TASKS_PER_USER = 3;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExamQuestionAssemblyTaskRepository taskRepository;
    private final QuestionAssemblyService questionAssemblyService;
    private final QuestionGenerationMaterialParser materialParser;
    private final AppMessageService appMessageService;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;

    public QuestionAssemblyTaskServiceImpl(
            ExamQuestionAssemblyTaskRepository taskRepository,
            QuestionAssemblyService questionAssemblyService,
            QuestionGenerationMaterialParser materialParser,
            AppMessageService appMessageService,
            ObjectMapper objectMapper,
            @Qualifier("questionAssemblyExecutor") Executor taskExecutor) {
        this.taskRepository = taskRepository;
        this.questionAssemblyService = questionAssemblyService;
        this.materialParser = materialParser;
        this.appMessageService = appMessageService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public TaskAccepted submit(
            AssemblyRequest request, MultipartFile file, Long userId, String authorization) {
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        if (taskRepository.countByUserIdAndStatusIn(userId, ACTIVE_STATUSES) >= MAX_ACTIVE_TASKS_PER_USER) {
            throw new BusinessException(429, "已有较多题库任务正在处理，请等待完成后再提交");
        }

        AssemblyRequest prepared = prepareRequest(request, file);
        ExamQuestionAssemblyTask task = new ExamQuestionAssemblyTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setUserId(userId);
        task.setStatus(ExamQuestionAssemblyTask.STATUS_QUEUED);
        task.setProgress(0);
        task.setMessage("题库任务已排队，可继续聊天或使用其他功能");
        task.setRequestJson(writeJson(prepared));
        task = taskRepository.save(task);

        Long databaseId = task.getId();
        try {
            taskExecutor.execute(() -> runTask(databaseId, authorization));
        } catch (RejectedExecutionException | IllegalStateException error) {
            failTask(task, "题库任务队列暂时繁忙，请稍后重试");
            throw new BusinessException(503, "题库任务队列暂时繁忙，请稍后重试");
        }

        TaskAccepted accepted = new TaskAccepted();
        accepted.setTaskId(task.getTaskId());
        accepted.setStatus(task.getStatus());
        accepted.setMessage(task.getMessage());
        return accepted;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        for (ExamQuestionAssemblyTask task : taskRepository.findByStatusIn(ACTIVE_STATUSES)) {
            failTask(task, "服务重启导致题库任务中断，请重新提交");
            notifySafely(task, false);
        }
    }

    @Override
    public TaskView get(String taskId, Long userId) {
        if (userId == null) throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        return toView(taskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(Result.NOT_FOUND_CODE, "题库任务不存在")));
    }

    private void runTask(Long databaseId, String authorization) {
        ExamQuestionAssemblyTask task = taskRepository.findById(databaseId).orElse(null);
        if (task == null) return;
        try {
            task.setStatus(ExamQuestionAssemblyTask.STATUS_RUNNING);
            task.setProgress(10);
            task.setMessage("题库智能体正在编排题目");
            task.setStartTime(LocalDateTime.now());
            taskRepository.save(task);

            AssemblyRequest request = objectMapper.readValue(task.getRequestJson(), AssemblyRequest.class);
            AssemblyResponse result = questionAssemblyService.generate(
                    request, null, task.getUserId(), authorization);
            int importedCount = 0;
            if (Boolean.TRUE.equals(request.getSaveGeneratedToPrivate())
                    && result.getGeneratedCount() != null && result.getGeneratedCount() > 0) {
                task.setProgress(85);
                task.setMessage("正在保存到你的私有题库");
                taskRepository.save(task);
                PrivateCommitResponse committed = questionAssemblyService.commitPrivate(
                        result.getDraftId(), task.getUserId());
                importedCount = committed.getImportedCount() == null ? 0 : committed.getImportedCount();
            }

            task.setStatus(ExamQuestionAssemblyTask.STATUS_SUCCEEDED);
            task.setProgress(100);
            task.setMessage(importedCount > 0
                    ? "题库生成完成，已保存到你的私有题库"
                    : "题库编排完成");
            task.setImportedCount(importedCount);
            task.setResultJson(writeJson(result));
            task.setCompleteTime(LocalDateTime.now());
            taskRepository.save(task);
            notifySafely(task, true);
        } catch (Exception error) {
            task = taskRepository.findById(databaseId).orElse(task);
            failTask(task, safeError(error));
            notifySafely(task, false);
        }
    }

    private AssemblyRequest prepareRequest(AssemblyRequest request, MultipartFile file) {
        if (request == null) throw new BusinessException(Result.BAD_REQUEST_CODE, "题库编排参数不能为空");
        AssemblyRequest copy;
        try {
            copy = objectMapper.readValue(objectMapper.writeValueAsBytes(request), AssemblyRequest.class);
        } catch (Exception error) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题库编排参数格式不正确");
        }
        if (file != null && !file.isEmpty()) {
            String sourceType = sourceType(copy, file);
            try {
                ParsedMaterial material = materialParser.parse(sourceType, file, copy.getText());
                copy.setText(material.text());
                copy.setSourceType("text");
                if (!StringUtils.hasText(copy.getSourceTitle())) copy.setSourceTitle(material.sourceTitle());
            } catch (IllegalArgumentException error) {
                throw new BusinessException(Result.BAD_REQUEST_CODE, error.getMessage());
            }
        }
        return copy;
    }

    private String sourceType(AssemblyRequest request, MultipartFile file) {
        if (StringUtils.hasText(request.getSourceType())
                && !"file".equalsIgnoreCase(request.getSourceType())) {
            return request.getSourceType().trim().toLowerCase();
        }
        String filename = String.valueOf(file.getOriginalFilename()).toLowerCase();
        return filename.endsWith(".docx") ? "docx" : "txt";
    }

    private void failTask(ExamQuestionAssemblyTask task, String message) {
        task.setStatus(ExamQuestionAssemblyTask.STATUS_FAILED);
        task.setProgress(100);
        task.setMessage("题库任务处理失败");
        task.setErrorMessage(truncate(message, 500));
        task.setCompleteTime(LocalDateTime.now());
        taskRepository.save(task);
    }

    private void createNotification(ExamQuestionAssemblyTask task, boolean success) {
        AppMessageDTO.CreateCommand command = new AppMessageDTO.CreateCommand();
        command.setUserId(task.getUserId());
        command.setModuleType(com.example.appbackend.entity.AppMessage.MODULE_EXAM);
        command.setEventType(success ? "QUESTION_ASSEMBLY_COMPLETED" : "QUESTION_ASSEMBLY_FAILED");
        command.setTitle(success ? "题库生成完成" : "题库生成失败");
        command.setContent(success ? task.getMessage() : task.getErrorMessage());
        command.setTargetPage("/subpackage_ai/examGenerate/examGenerate");
        command.setTargetParams("{\"taskId\":\"" + task.getTaskId() + "\"}");
        command.setSourceId(task.getId());
        command.setSourceType("QUESTION_ASSEMBLY_TASK");
        appMessageService.createIfAbsent(command);
    }

    private void notifySafely(ExamQuestionAssemblyTask task, boolean success) {
        try {
            createNotification(task, success);
        } catch (RuntimeException ignored) {
            // Task state remains authoritative; the App can still retrieve it by task ID.
        }
    }

    private TaskView toView(ExamQuestionAssemblyTask task) {
        TaskView view = new TaskView();
        view.setTaskId(task.getTaskId());
        view.setStatus(task.getStatus());
        view.setProgress(task.getProgress());
        view.setMessage(task.getMessage());
        view.setErrorMessage(task.getErrorMessage());
        view.setImportedCount(task.getImportedCount());
        if (StringUtils.hasText(task.getResultJson())) {
            try {
                view.setResult(objectMapper.readValue(task.getResultJson(), AssemblyResponse.class));
            } catch (Exception ignored) {
                view.setErrorMessage("任务结果暂时无法读取");
            }
        }
        view.setCreateTime(format(task.getCreateTime()));
        view.setStartTime(format(task.getStartTime()));
        view.setCompleteTime(format(task.getCompleteTime()));
        return view;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new BusinessException(Result.ERROR_CODE, "题库任务数据保存失败");
        }
    }

    private String safeError(Exception error) {
        if (error instanceof BusinessException && StringUtils.hasText(error.getMessage())) {
            return error.getMessage();
        }
        return "题库智能体暂时无法完成任务，请稍后重试";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMAT);
    }
}
