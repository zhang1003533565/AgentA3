package com.example.appbackend.controller;

import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyOptions;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyRequest;
import com.example.appbackend.dto.QuestionAssemblyDTO.AssemblyResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.PrivateCommitResponse;
import com.example.appbackend.dto.QuestionAssemblyDTO.TaskAccepted;
import com.example.appbackend.dto.QuestionAssemblyDTO.TaskView;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.QuestionAssemblyService;
import com.example.appbackend.service.QuestionAssemblyTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/exam/question-assembly")
public class QuestionAssemblyController {

    private final QuestionAssemblyService questionAssemblyService;
    private final QuestionAssemblyTaskService questionAssemblyTaskService;
    private final ObjectMapper objectMapper;

    public QuestionAssemblyController(
            QuestionAssemblyService questionAssemblyService,
            QuestionAssemblyTaskService questionAssemblyTaskService,
            ObjectMapper objectMapper) {
        this.questionAssemblyService = questionAssemblyService;
        this.questionAssemblyTaskService = questionAssemblyTaskService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/options")
    public Result<AssemblyOptions> options(HttpServletRequest request) {
        getUserId(request);
        return Result.success(questionAssemblyService.options(request.getHeader("Authorization")));
    }

    @PostMapping(value = "/generate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AssemblyResponse> generate(
            @RequestParam("spec") String specJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) {
        AssemblyRequest spec;
        try {
            spec = objectMapper.readValue(specJson, AssemblyRequest.class);
        } catch (Exception error) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题库编排参数格式不正确");
        }
        return Result.success(questionAssemblyService.generate(
                spec, file, getUserId(request), request.getHeader("Authorization")));
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<AssemblyResponse> generateJson(
            @Valid @RequestBody AssemblyRequest spec,
            HttpServletRequest request) {
        return Result.success(questionAssemblyService.generate(
                spec, null, getUserId(request), request.getHeader("Authorization")));
    }

    @PostMapping(value = "/tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<TaskAccepted> submitTask(
            @Valid @RequestBody AssemblyRequest spec,
            HttpServletRequest request) {
        return Result.success("题库任务已提交，可继续聊天",
                questionAssemblyTaskService.submit(
                        spec, null, getUserId(request), request.getHeader("Authorization")));
    }

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<TaskAccepted> submitFileTask(
            @RequestParam("spec") String specJson,
            @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) {
        AssemblyRequest spec = readSpec(specJson);
        return Result.success("题库任务已提交，可继续聊天",
                questionAssemblyTaskService.submit(
                        spec, file, getUserId(request), request.getHeader("Authorization")));
    }

    @GetMapping("/tasks/{taskId}")
    public Result<TaskView> task(
            @PathVariable String taskId,
            HttpServletRequest request) {
        return Result.success(questionAssemblyTaskService.get(taskId, getUserId(request)));
    }

    @PostMapping("/{draftId}/commit-private")
    public Result<PrivateCommitResponse> commitPrivate(
            @PathVariable String draftId, HttpServletRequest request) {
        return Result.success("已保存到个人私有题库",
                questionAssemblyService.commitPrivate(draftId, getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (!(userId instanceof Long id)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return id;
    }

    private AssemblyRequest readSpec(String specJson) {
        try {
            return objectMapper.readValue(specJson, AssemblyRequest.class);
        } catch (Exception error) {
            throw new BusinessException(Result.BAD_REQUEST_CODE, "题库编排参数格式不正确");
        }
    }
}
