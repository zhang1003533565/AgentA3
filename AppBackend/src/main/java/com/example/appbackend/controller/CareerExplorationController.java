package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CareerExplorationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/career-exploration")
public class CareerExplorationController {
    private final CareerExplorationService service;

    public CareerExplorationController(CareerExplorationService service) {
        this.service = service;
    }

    @GetMapping("/careers/{careerId}")
    public Result<?> career(@PathVariable String careerId, HttpServletRequest request) {
        return Result.success(service.career(careerId, userId(request)));
    }

    @GetMapping("/careers/{careerId}/planets/{skillId}")
    public Result<?> planet(@PathVariable String careerId, @PathVariable String skillId,
                            HttpServletRequest request) {
        return Result.success(service.planet(careerId, skillId, userId(request)));
    }

    @GetMapping("/careers/{careerId}/planets/{skillId}/chapters/{chapterId}")
    public Result<?> chapter(@PathVariable String careerId, @PathVariable String skillId,
            @PathVariable Long chapterId, HttpServletRequest request) {
        return Result.success(service.chapter(careerId, skillId, chapterId, userId(request)));
    }

    @PutMapping("/careers/{careerId}/planets/{skillId}/chapters/{chapterId}/video-progress")
    public Result<?> videoProgress(@PathVariable String careerId, @PathVariable String skillId,
            @PathVariable Long chapterId, @RequestBody VideoProgressRequest body, HttpServletRequest request) {
        return Result.success(service.updateVideoProgress(careerId, skillId, chapterId, body.materialId(),
                body.currentSeconds(), body.durationSeconds(), body.watchedSecondsDelta(), userId(request)));
    }

    @PostMapping("/careers/{careerId}/planets/{skillId}/chapters/{chapterId}/questions/{questionId}/answer")
    public Result<?> answerQuestion(@PathVariable String careerId, @PathVariable String skillId,
            @PathVariable Long chapterId, @PathVariable String questionId,
            @RequestBody ChapterAnswerRequest body, HttpServletRequest request) {
        return Result.success(service.answerChapterQuestion(careerId, skillId, chapterId,
                questionId, body.answer(), userId(request)));
    }

    @PostMapping("/careers/{careerId}/planets/{skillId}/chapters/{chapterId}/complete")
    public Result<?> completeChapter(@PathVariable String careerId, @PathVariable String skillId,
                                     @PathVariable Long chapterId, HttpServletRequest request) {
        return Result.success("章节学习已完成",
                service.completeChapter(careerId, skillId, chapterId, userId(request)));
    }

    @GetMapping("/careers/{careerId}/planets/{skillId}/final-exam")
    public Result<?> finalExam(@PathVariable String careerId, @PathVariable String skillId,
            HttpServletRequest request) {
        return Result.success(service.finalExamEntry(careerId, skillId, userId(request)));
    }

    @PostMapping("/careers/{careerId}/planets/{skillId}/sync-final-exam")
    public Result<?> syncFinalExam(@PathVariable String careerId, @PathVariable String skillId,
            @RequestBody SyncExamRequest body, HttpServletRequest request) {
        return Result.success(service.syncFinalExam(careerId, skillId, body.attemptId(), userId(request)));
    }

    public record VideoProgressRequest(Long materialId, int currentSeconds, int durationSeconds,
                                       int watchedSecondsDelta) { }
    public record SyncExamRequest(Long attemptId) { }
    public record ChapterAnswerRequest(String answer) { }

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) throw new BusinessException(401, "请先登录");
        return id;
    }
}
