package com.example.appbackend.controller;

import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.dto.UserLikeResponse;
import com.example.appbackend.dto.UserPostResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forum/users")
public class UserCenterController {

    @Autowired
    private PostService postService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    private Long requireCurrentUserId(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "请先登录");
        }
        return userId;
    }

    @GetMapping("/{userId}/posts")
    public Result<PageResponse<UserPostResponse>> getUserPosts(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        requireCurrentUserId(request);
        return Result.success("操作成功", postService.getUserPost(userId, pageNum, pageSize));
    }

    @GetMapping("/posts/me")
    public Result<PageResponse<UserPostResponse>> getMyPosts(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = requireCurrentUserId(request);
        return Result.success("操作成功", postService.getUserPost(userId, pageNum, pageSize));
    }

    @GetMapping("/{userId}/likes")
    public Result<PageResponse<UserLikeResponse>> getUserLikes(
            HttpServletRequest request,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        requireCurrentUserId(request);
        return Result.success("操作成功", postService.getUserLikes(userId, pageNum, pageSize));
    }
}
