package com.example.appbackend.controller;

import com.example.appbackend.dto.LearningPathDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.LearningWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/app/learning")
public class AppLearningController {

    private final LearningWorkflowService learningWorkflowService;

    public AppLearningController(LearningWorkflowService learningWorkflowService) {
        this.learningWorkflowService = learningWorkflowService;
    }

    @PostMapping(value = "/resources/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateResources(
            @Valid @RequestBody LearningPathDTO.GenerateRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return learningWorkflowService.start(requireUserId(request), body, authorization);
    }

    @GetMapping("/workflows/{workflowId}")
    public Result<LearningPathDTO.WorkflowView> workflow(
            @PathVariable String workflowId,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.getWorkflow(requireUserId(request), workflowId));
    }

    @GetMapping("/courses/python/home")
    public Result<LearningPathDTO.HomeView> home(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.getPythonHome(requireUserId(request), authorization));
    }

    @PostMapping("/courses/python/profile-answers")
    public Result<LearningPathDTO.ProfileAnswerResult> profileAnswer(
            @Valid @RequestBody LearningPathDTO.ProfileAnswerRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.answerProfile(
                requireUserId(request), body, authorization));
    }

    @GetMapping("/courses/python/path")
    public Result<LearningPathDTO.PathView> path(HttpServletRequest request) {
        return Result.success(learningWorkflowService.getPythonPath(requireUserId(request)));
    }

    @GetMapping("/courses/python/recommendations")
    public Result<List<LearningPathDTO.Recommendation>> recommendations(HttpServletRequest request) {
        return Result.success(learningWorkflowService.getPythonRecommendations(requireUserId(request)));
    }

    @PostMapping("/recommendations/{itemId}/interactions")
    public Result<Void> recommendationInteraction(
            @PathVariable Long itemId,
            @Valid @RequestBody LearningPathDTO.InteractionRequest body,
            HttpServletRequest request) {
        learningWorkflowService.recordRecommendationInteraction(requireUserId(request), itemId, body);
        return Result.success();
    }

    @PostMapping("/path-items/{itemId}/start")
    public Result<LearningPathDTO.PathItemView> startPathItem(
            @PathVariable Long itemId,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.startPathItem(requireUserId(request), itemId));
    }

    @PostMapping("/path-items/{itemId}/complete")
    public Result<LearningPathDTO.PathItemView> completePathItem(
            @PathVariable Long itemId,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.completePathItem(requireUserId(request), itemId));
    }

    @PostMapping("/courses/python/path/replan")
    public Result<LearningPathDTO.PathView> replan(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.replanPythonPath(
                requireUserId(request), authorization));
    }

    @PostMapping("/workflows/{workflowId}/resources/{resourceType}/retry")
    public Result<LearningPathDTO.WorkflowView> retryResource(
            @PathVariable String workflowId,
            @PathVariable String resourceType,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        return Result.success(learningWorkflowService.retryResource(
                requireUserId(request), workflowId, resourceType, authorization));
    }

    private Long requireUserId(HttpServletRequest request) {
        Object raw = request.getAttribute("userId");
        if (!(raw instanceof Number number)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return number.longValue();
    }
}
