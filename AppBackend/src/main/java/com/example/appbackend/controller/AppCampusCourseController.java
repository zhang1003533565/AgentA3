package com.example.appbackend.controller;

import com.example.appbackend.dto.CampusCourseDTO;
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

    private Long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof Long id)) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }
}
