package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.UserLikeResponse;
import com.example.appbackend.dto.UserPostResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/users")
@Tag(name = "用户中心", description = "用户帖子、点赞等接口")
public class UserCenterController {
    private void checkId(HttpServletRequest request){
        Long id=(Long) request.getAttribute("userId");
        if(id==null){
            throw new BusinessException(Result.FORBIDDEN_CODE,"请登录账号");
        }
    }
    @Autowired
    private PostService postService;

    @Operation(summary = "获取用户帖子列表", description = "获取指定用户的帖子列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/{userId}/posts")
    public Result<PageResponse<UserPostResponse>> getUserPosts(
            HttpServletRequest request,
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize) {
        checkId(request);
        PageResponse<UserPostResponse> userPost = postService.getUserPost(userId, pageNum, pageSize);
        return Result.success("操作成功", userPost);
    }

    @Operation(summary = "获取用户点赞列表", description = "获取指定用户点赞的帖子列表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/{userId}/likes")
    public Result<PageResponse<UserLikeResponse>> getUserLikes(
            HttpServletRequest request,
            @Parameter(description = "用户ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer pageSize) {
        checkId(request);
        PageResponse<UserLikeResponse> userLikes = postService.getUserLikes(userId, pageNum, pageSize);
        return Result.success("操作成功", userLikes);
    }
}
