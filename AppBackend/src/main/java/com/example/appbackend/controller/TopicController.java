package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.TopicRequest;
import com.example.appbackend.dto.TopicResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum/topics")
@Tag(name = "话题管理", description = "话题的创建、更新、删除、查询等接口")
public class TopicController {

    @Autowired
    private TopicService topicService;

    private void checkRole(HttpServletRequest request){
        String role=(String) request.getAttribute("role");
        if(!role.equals("TEACHER")&&!role.equals("ADMIN")){
            throw new BusinessException(Result.FORBIDDEN_CODE,"仅教师和管理员可操作");
        }

    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : null;
    }

    @Operation(summary = "获取话题列表", description = "分页获取话题列表，支持筛选")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping
    public Result<PageResponse<TopicResponse>> getTopicList(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "筛选热门")
            @RequestParam(required = false) Integer isHot,
            @Parameter(description = "状态筛选")
            @RequestParam(required = false) String status) {
        PageResponse<TopicResponse> page = topicService.getTopicList(pageNum, pageSize, isHot, status);
        return Result.success("操作成功", page);
    }

    @Operation(summary = "获取热门话题", description = "获取热门话题列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功")
    })
    @GetMapping("/hot")
    public Result<List<TopicResponse>> getHotTopics(
            @Parameter(description = "返回数量", example = "5")
            @RequestParam(defaultValue = "5") Integer limit) {
        List<TopicResponse> hotTopics = topicService.getHotTopics(limit);
        return Result.success("操作成功", hotTopics);
    }

    @Operation(summary = "创建话题", description = "创建新话题（管理端）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PostMapping
    public Result<TopicResponse> createTopic(
            @Valid @RequestBody TopicRequest topicRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkRole(request);
        TopicResponse topic = topicService.createTopic(topicRequest);
        return Result.success("创建成功", topic);
    }

    @Operation(summary = "更新话题", description = "更新话题信息（管理端）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "话题不存在")
    })
    @PutMapping("/{id}")
    public Result<TopicResponse> updateTopic(
            @Parameter(description = "话题ID", required = true, example = "1")
            @PathVariable Long id,
            @Valid @RequestBody TopicRequest topicRequest,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkRole(request);
        TopicResponse topic = topicService.updateTopic(id, topicRequest);
        return Result.success("更新成功", topic);
    }

    @Operation(summary = "删除话题", description = "删除话题（管理端）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "话题不存在")
    })
    @DeleteMapping("/{id}")
    public Result<Void> deleteTopic(
            @Parameter(description = "话题ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkRole(request);
        topicService.deleteTopic(id);
        return Result.success("删除成功", null);
    }
}
