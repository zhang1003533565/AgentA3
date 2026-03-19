package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CommentService;
import com.example.appbackend.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum/audit")
@Tag(name = "内容审核", description = "帖子和评论的审核管理接口")
public class AuditController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private PostService postService;

    private void checkAdmin(HttpServletRequest request){
        String role =(String) request.getAttribute("role");
        if(!role.equals("ADMIN")){
            throw new BusinessException(Result.FORBIDDEN_CODE,"只有管理员可操作");
        }
    }

    @Operation(summary = "删除评论", description = "删除指定评论（管理员操作）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限"),
            @ApiResponse(responseCode = "404", description = "评论不存在")
    })
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(
            @Parameter(description = "评论ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        commentService.deleteCommentByAdmin(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "删除帖子", description = "删除指定帖子（管理员操作）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限"),
            @ApiResponse(responseCode = "404", description = "帖子不存在")
    })
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(
            @Parameter(description = "帖子ID", required = true, example = "1")
            @PathVariable Long id,
            HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        postService.deletePostByAdmin(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "批量删除帖子", description = "批量删除指定帖子（管理员操作）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @DeleteMapping("/posts/batch")
    public Result<Void> batchDeletePosts(
            @Parameter(description = "帖子ID列表", required = true)
            @RequestBody List<Long> ids,
            HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        postService.batchDeletePosts(ids);
        return Result.success("批量删除成功，共删除 " + ids.size() + " 条帖子", null);
    }

    @Operation(summary = "批量删除评论", description = "批量删除指定评论（管理员操作）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "删除成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无权限")
    })
    @DeleteMapping("/comments/batch")
    public Result<Void> batchDeleteComments(
            @Parameter(description = "评论ID列表", required = true)
            @RequestBody List<Long> ids,
            HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("请先登录");
        }
        checkAdmin(request);
        commentService.batchDeleteComments(ids);
        return Result.success("批量删除成功，共删除 " + ids.size() + " 条评论", null);
    }

}



