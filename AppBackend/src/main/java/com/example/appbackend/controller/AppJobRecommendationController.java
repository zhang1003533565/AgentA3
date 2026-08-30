package com.example.appbackend.controller;

import com.example.appbackend.dto.WeeklyJobRecommendationDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.WeeklyJobRecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/job-recommendations")
public class AppJobRecommendationController {

    private final WeeklyJobRecommendationService weeklyJobRecommendationService;

    public AppJobRecommendationController(WeeklyJobRecommendationService weeklyJobRecommendationService) {
        this.weeklyJobRecommendationService = weeklyJobRecommendationService;
    }

    @GetMapping("/latest")
    public Result<List<WeeklyJobRecommendationDTO>> latest(HttpServletRequest request) {
        try {
            return Result.success(weeklyJobRecommendationService.listLatestOrRefresh(authorization(request)));
        } catch (IllegalStateException exception) {
            throw new BusinessException(Result.ERROR_CODE, exception.getMessage());
        }
    }

    @PostMapping("/refresh")
    public Result<List<WeeklyJobRecommendationDTO>> refresh(HttpServletRequest request) {
        try {
            return Result.success(weeklyJobRecommendationService.refreshCurrentWeek(authorization(request)));
        } catch (IllegalStateException exception) {
            throw new BusinessException(Result.ERROR_CODE, exception.getMessage());
        }
    }

    private String authorization(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }
}
