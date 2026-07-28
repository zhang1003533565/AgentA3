package com.example.appbackend.controller;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CampusCourseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/campus-courses")
public class AdminCampusCourseController {
    private final CampusCourseService courseService;

    public AdminCampusCourseController(CampusCourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        adminId(request);
        return Result.success(courseService.adminList());
    }

    @GetMapping("/{courseId}")
    public Result<?> detail(@PathVariable Long courseId, HttpServletRequest request) {
        adminId(request);
        return Result.success(courseService.adminDetail(courseId));
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody CampusCourseDTO.SaveRequest body,
                            HttpServletRequest request) {
        return Result.success("课程已创建", courseService.create(body, adminId(request)));
    }

    @PutMapping("/{courseId}")
    public Result<?> update(@PathVariable Long courseId,
                            @Valid @RequestBody CampusCourseDTO.SaveRequest body,
                            HttpServletRequest request) {
        adminId(request);
        return Result.success("课程已保存", courseService.update(courseId, body));
    }

    @PostMapping("/{courseId}/publish")
    public Result<?> publish(@PathVariable Long courseId, HttpServletRequest request) {
        return Result.success("课程已发布", courseService.publish(courseId, adminId(request)));
    }

    @PostMapping("/{courseId}/offline")
    public Result<?> offline(@PathVariable Long courseId, HttpServletRequest request) {
        adminId(request);
        return Result.success("课程已下架", courseService.offline(courseId));
    }

    @DeleteMapping("/{courseId}")
    public Result<Void> delete(@PathVariable Long courseId, HttpServletRequest request) {
        adminId(request);
        courseService.delete(courseId);
        return Result.success("课程已删除", null);
    }

    @PostMapping("/{courseId}/chapters")
    public Result<?> createChapter(@PathVariable Long courseId,
                                   @Valid @RequestBody CampusCourseDTO.ChapterSaveRequest body,
                                   HttpServletRequest request) {
        adminId(request);
        return Result.success("章节已添加", courseService.createChapter(courseId, body));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}")
    public Result<?> updateChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
                                   @Valid @RequestBody CampusCourseDTO.ChapterSaveRequest body,
                                   HttpServletRequest request) {
        adminId(request);
        return Result.success("章节已保存", courseService.updateChapter(courseId, chapterId, body));
    }

    @DeleteMapping("/{courseId}/chapters/{chapterId}")
    public Result<Void> deleteChapter(@PathVariable Long courseId, @PathVariable Long chapterId,
                                      HttpServletRequest request) {
        adminId(request);
        courseService.deleteChapter(courseId, chapterId);
        return Result.success("章节已删除", null);
    }

    @PostMapping("/{courseId}/exams")
    public Result<?> linkExam(@PathVariable Long courseId,
                              @Valid @RequestBody CampusCourseDTO.ExamLinkRequest body,
                              HttpServletRequest request) {
        return Result.success("考试已关联", courseService.linkExam(courseId, body, adminId(request)));
    }

    @DeleteMapping("/{courseId}/exams/{linkId}")
    public Result<Void> unlinkExam(@PathVariable Long courseId, @PathVariable Long linkId,
                                   HttpServletRequest request) {
        adminId(request);
        courseService.unlinkExam(courseId, linkId);
        return Result.success("考试关联已移除", null);
    }

    private Long adminId(HttpServletRequest request) {
        if (!"ADMIN".equals(request.getAttribute("role"))) {
            throw new BusinessException(403, "仅管理员可管理校园课程");
        }
        Object userId = request.getAttribute("userId");
        if (!(userId instanceof Long id)) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }
}
