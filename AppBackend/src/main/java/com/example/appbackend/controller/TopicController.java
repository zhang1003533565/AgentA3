package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/topics")
@Tag(name = "话题管理", description = "话题的创建、更新、删除、查询等接口")
public class TopicController {

    @Operation(summary = "获取话题列表", description = "分页获取话题列表，支持筛选")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping
    public Result<Void> getTopicList(
            @Parameter(description = "页码", example = "1") 
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20") 
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "筛选热门") 
            @RequestParam(required = false) Integer isHot,
            @Parameter(description = "状态筛选") 
            @RequestParam(required = false) String status) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "获取热门话题", description = "获取热门话题列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/hot")
    public Result<Void> getHotTopics(
            @Parameter(description = "返回数量", example = "5") 
            @RequestParam(defaultValue = "5") Integer limit) {
        return Result.success("操作成功", null);
    }

    @Operation(summary = "创建话题", description = "创建新话题（管理端）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限")
    })
    @PostMapping
    public Result<Void> createTopic(@RequestBody String topicJson, HttpServletRequest request) {
        return Result.success("创建成功", null);
    }

    @Operation(summary = "更新话题", description = "更新话题信息（管理端）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "403", description = "无权限"),
        @ApiResponse(responseCode = "404", description = "话题不存在")
    })
    @PutMapping("/{id}")
    public Result<Void> updateTopic(
            @Parameter(description = "话题ID", required = true, example = "1") 
            @PathVariable Long id,
            @RequestBody String topicJson, 
            HttpServletRequest request) {
        return Result.success("更新成功", null);
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
        return Result.success("删除成功", null);
    }
}
