package com.example.appbackend.controller;

import com.example.appbackend.dto.MaterialDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.MaterialProgressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 精细化学习进度接口（学生端，仅新增）。
 * 不与旧的章节打卡接口冲突：小程序新版详情/进度 UI 统一走本接口。
 */
@RestController
@RequestMapping("/api/app/progress")
public class AppProgressController {

    private final MaterialProgressService progressService;

    public AppProgressController(MaterialProgressService progressService) {
        this.progressService = progressService;
    }

    /** 上报资料观看进度（每 5 秒节流上报一次）。 */
    @PostMapping("/material/report")
    public Result<?> report(@Valid @RequestBody MaterialDTO.ProgressReportRequest body,
                            HttpServletRequest request) {
        progressService.report(userId(request), body.getMaterialId(), body.getWatchSeconds());
        return Result.success();
    }

    /** 查询当前用户在该课程下的资料列表 + 精细进度（章节级/课程级）。 */
    @GetMapping("/course/{courseId}")
    public Result<?> courseProgress(@PathVariable Long courseId, HttpServletRequest request) {
        return Result.success(progressService.courseProgress(userId(request), courseId));
    }

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }
}
