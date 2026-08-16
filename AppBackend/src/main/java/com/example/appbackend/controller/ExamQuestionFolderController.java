package com.example.appbackend.controller;

import com.example.appbackend.dto.ExamQuestionDTO;
import com.example.appbackend.dto.ExamQuestionFolderDTO;
import com.example.appbackend.dto.PageResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ExamQuestionFolderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exam/question-folders")
@Tag(name = "我的题库收藏夹", description = "公共/私有题库收藏夹管理")
public class ExamQuestionFolderController {

    private final ExamQuestionFolderService folderService;

    public ExamQuestionFolderController(ExamQuestionFolderService folderService) {
        this.folderService = folderService;
    }

    @GetMapping
    @Operation(summary = "收藏夹列表", description = "按 PUBLIC/PRIVATE 获取可见收藏夹；管理员可筛选他人私有收藏夹")
    public Result<List<ExamQuestionFolderDTO.FolderVO>> list(
            @RequestParam String visibility,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String ownerKeyword,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        boolean admin = isAdmin(request);
        return Result.success(folderService.listFolders(visibility, userId, admin, ownerUserId, ownerKeyword));
    }

    @PostMapping
    @Operation(summary = "创建收藏夹")
    public Result<ExamQuestionFolderDTO.FolderVO> create(
            @Valid @RequestBody ExamQuestionFolderDTO.CreateRequest body,
            HttpServletRequest request) {
        return Result.success("创建成功", folderService.createFolder(body, getUserId(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "重命名收藏夹")
    public Result<ExamQuestionFolderDTO.FolderVO> rename(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionFolderDTO.RenameRequest body,
            HttpServletRequest request) {
        return Result.success("已重命名", folderService.renameFolder(id, body, getUserId(request), isAdmin(request)));
    }

    @PutMapping("/{id}/visibility")
    @Operation(summary = "切换收藏夹公共/私有")
    public Result<ExamQuestionFolderDTO.FolderVO> changeVisibility(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionFolderDTO.VisibilityRequest body,
            HttpServletRequest request) {
        return Result.success(
                "可见范围已更新",
                folderService.changeVisibility(id, body, getUserId(request), isAdmin(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除收藏夹")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        folderService.deleteFolder(id, getUserId(request), isAdmin(request));
        return Result.success("已删除", null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "收藏夹详情")
    public Result<ExamQuestionFolderDTO.FolderDetailVO> detail(
            @PathVariable Long id,
            HttpServletRequest request) {
        return Result.success(folderService.getFolderDetail(id, getUserId(request), isAdmin(request)));
    }

    @GetMapping("/{id}/questions")
    @Operation(summary = "收藏夹题目列表")
    public Result<PageResponse<ExamQuestionDTO.QuestionVO>> questions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        return Result.success(folderService.listFolderQuestions(
                id, current, size, getUserId(request), isAdmin(request)));
    }

    @PostMapping("/{id}/questions")
    @Operation(summary = "向收藏夹添加题目")
    public Result<Void> addQuestion(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionFolderDTO.AddQuestionRequest body,
            HttpServletRequest request) {
        folderService.addQuestion(id, body.getQuestionId(), getUserId(request), isAdmin(request));
        return Result.success("已添加", null);
    }

    @PostMapping("/{id}/questions/push")
    @Operation(summary = "将题目推送到其他收藏夹", description = "支持公共↔私有；推到公共夹时可同步公开私有题")
    public Result<ExamQuestionFolderDTO.PushQuestionsResult> pushQuestions(
            @PathVariable Long id,
            @Valid @RequestBody ExamQuestionFolderDTO.PushQuestionsRequest body,
            HttpServletRequest request) {
        return Result.success(
                "推送成功",
                folderService.pushQuestions(id, body, getUserId(request), isAdmin(request)));
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    @Operation(summary = "从收藏夹移除题目")
    public Result<Void> removeQuestion(
            @PathVariable Long id,
            @PathVariable Long questionId,
            HttpServletRequest request) {
        folderService.removeQuestion(id, questionId, getUserId(request), isAdmin(request));
        return Result.success("已移除", null);
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "ADMIN".equals(request.getAttribute("role"));
    }

    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (!(userId instanceof Long id)) {
            throw new BusinessException(Result.UNAUTHORIZED_CODE, "请先登录");
        }
        return id;
    }
}
