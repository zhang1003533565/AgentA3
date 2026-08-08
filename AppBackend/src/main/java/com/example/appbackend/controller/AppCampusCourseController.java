package com.example.appbackend.controller;

import com.example.appbackend.dto.CampusCourseDTO;
import com.example.appbackend.dto.WordContentDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.CampusCourseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app/campus-courses")
public class AppCampusCourseController {
    private final CampusCourseService courseService;

    public AppCampusCourseController(CampusCourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public Result<?> list(HttpServletRequest request) {
        return Result.success(courseService.studentList(userId(request)));
    }

    @GetMapping("/my")
    public Result<?> myCourses(HttpServletRequest request) {
        return Result.success(courseService.myEnrolledCourses(userId(request)));
    }

    @GetMapping("/{courseId}")
    public Result<?> detail(@PathVariable Long courseId, HttpServletRequest request) {
        return Result.success(courseService.studentDetail(courseId, userId(request)));
    }

    @PutMapping("/{courseId}/chapters/{chapterId}/progress")
    public Result<?> progress(@PathVariable Long courseId, @PathVariable Long chapterId,
                              @Valid @RequestBody CampusCourseDTO.ProgressRequest body,
                              HttpServletRequest request) {
        return Result.success(courseService.updateProgress(
                courseId, chapterId, userId(request), body.getCompleted()));
    }

    @GetMapping("/{courseId}/chapters/{chapterId}")
    public Result<?> chapterDetail(@PathVariable Long courseId, @PathVariable Long chapterId,
                                   HttpServletRequest request) {
        return Result.success(courseService.chapterDetail(courseId, chapterId, userId(request)));
    }

    @PostMapping("/{courseId}/enroll")
    public Result<?> enroll(@PathVariable Long courseId, HttpServletRequest request) {
        courseService.enroll(courseId, userId(request));
        return Result.success("已加入课程", null);
    }

    @DeleteMapping("/{courseId}/enroll")
    public Result<?> unenroll(@PathVariable Long courseId, HttpServletRequest request) {
        courseService.unenroll(courseId, userId(request));
        return Result.success("已移出课程", null);
    }

    @GetMapping("/{courseId}/chapters/{chapterId}/resources")
    public Result<?> chapterResources(@PathVariable Long courseId, @PathVariable Long chapterId,
                                      HttpServletRequest request) {
        return Result.success(courseService.chapterResources(courseId, chapterId, userId(request)));
    }

    @GetMapping("/{courseId}/chapters/{chapterId}/word/{materialId}/content")
    public Result<WordContentDTO.PageResponse> wordContent(
            @PathVariable Long courseId, @PathVariable Long chapterId,
            @PathVariable Long materialId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "500") int size,
            HttpServletRequest request) {
        return Result.success(courseService.wordContent(
                courseId, chapterId, materialId, page, size, userId(request)));
    }

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }
}
