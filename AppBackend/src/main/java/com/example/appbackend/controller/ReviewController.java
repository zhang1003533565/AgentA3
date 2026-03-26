package com.example.appbackend.controller;

import com.example.appbackend.dto.ReviewRequest;
import com.example.appbackend.dto.ReviewPageResponse;
import com.example.appbackend.entity.FacilityReview;
import com.example.appbackend.entity.Result;
import com.example.appbackend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/review")
@Tag(name = "设施评价", description = "设施评价的提交、查询、删除接口")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping
    @Operation(summary = "提交评价", description = "用户对设施提交评分和评价")
    public Result<FacilityReview> createReview(
            @Valid @RequestBody ReviewRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        FacilityReview review = reviewService.createReview(request, userId);
        return Result.success("评价提交成功", review);
    }

    @GetMapping("/list")
    @Operation(summary = "获取评价列表", description = "分页查询评价列表，包含评分统计信息")
    public Result<ReviewPageResponse> getReviewList(
            @Parameter(description = "设施ID")
            @RequestParam(required = false) Long facilityId,
            @Parameter(description = "页码，默认1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量，默认10")
            @RequestParam(defaultValue = "10") Integer pageSize) {
        ReviewPageResponse result = reviewService.getReviewList(facilityId, pageNum, pageSize);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除评价", description = "删除指定ID的评价（仅评价作者可删除）")
    public Result<Void> deleteReview(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        reviewService.deleteReview(id, userId);
        return Result.success("评价删除成功", null);
    }
}
