package com.example.appbackend.controller;

import com.example.appbackend.dto.WeeklyJobRecommendationDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.JobFavoriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/app/job-favorites")
public class AppJobFavoriteController {

    private final JobFavoriteService jobFavoriteService;

    public AppJobFavoriteController(JobFavoriteService jobFavoriteService) {
        this.jobFavoriteService = jobFavoriteService;
    }

    @GetMapping
    public Result<List<WeeklyJobRecommendationDTO>> list(HttpServletRequest request) {
        return Result.success(jobFavoriteService.listFavoriteJobs(userId(request)));
    }

    @GetMapping("/ids")
    public Result<Set<Long>> ids(HttpServletRequest request) {
        return Result.success(jobFavoriteService.getFavoriteRecommendationIds(userId(request)));
    }

    @PostMapping("/{recommendationId}")
    public Result<Void> add(@PathVariable Long recommendationId, HttpServletRequest request) {
        jobFavoriteService.addFavorite(userId(request), recommendationId);
        return Result.success();
    }

    @DeleteMapping("/{recommendationId}")
    public Result<Void> remove(@PathVariable Long recommendationId, HttpServletRequest request) {
        jobFavoriteService.removeFavorite(userId(request), recommendationId);
        return Result.success();
    }

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return id;
    }
}
