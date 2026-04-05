package com.example.appbackend.controller;

import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.util.WeekCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedule")
@Tag(name = "课表管理", description = "课表查询、复制等接口")
public class CourseScheduleController {

    @Autowired
    private CourseScheduleService courseScheduleService;

    @Autowired
    private UserRepository userRepository;

    private Long getCurrentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    @Operation(summary = "获取用户课表", description = "获取当前用户的所有课表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping
    public Result<List<CourseSchedule>> getSchedule(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<CourseSchedule> schedules = courseScheduleService.getUserSchedule(userId);
        return Result.success(schedules);
    }

    @Operation(summary = "获取本周课表", description = "获取当前用户本周的课表（返回格式兼容前端课表页面）")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentSchedule(HttpServletRequest request) {
        try {
            Long userId = getCurrentUserId(request);
            var schedule = courseScheduleService.getCurrentWeekSchedule(userId);

            // 获取用户信息用于计算学期
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            // 计算当前周次
            int currentWeek = WeekCalculator.getCurrentWeek(user.getSemesterStart());

            Map<String, Object> result = new HashMap<>();
            result.put("currentWeek", currentWeek);
            result.put("count", schedule.size());
            result.put("schedule", schedule);
            result.put("semester", calculateSemester(user.getSemesterStart()));
            if (user.getSemesterStart() != null) {
                result.put("semesterStart", user.getSemesterStart().toString());
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取失败：" + e.getMessage());
        }
    }

    private String calculateSemester(java.time.LocalDate semesterStart) {
        if (semesterStart == null) {
            return "2025-2026 第 1 学期";
        }
        int year = semesterStart.getYear();
        int month = semesterStart.getMonthValue();
        if (month >= 2 && month <= 8) {
            return year + "-" + (year + 1) + " 第 1 学期";
        } else {
            return (year - 1) + "-" + year + " 第 2 学期";
        }
    }

    @Operation(summary = "获取本周课表", description = "获取当前用户本周的课表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/current-week")
    public Result<List<CourseSchedule>> getCurrentWeekSchedule(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<CourseSchedule> schedules = courseScheduleService.getCurrentWeekSchedule(userId);
        return Result.success(schedules);
    }

    @Operation(summary = "获取指定周次课表", description = "获取当前用户指定周次的课表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/week/{week}")
    public Result<List<CourseSchedule>> getWeekSchedule(
            HttpServletRequest request,
            @Parameter(description = "周次", required = true, example = "1")
            @PathVariable Integer week) {
        Long userId = getCurrentUserId(request);
        List<CourseSchedule> schedules = courseScheduleService.getWeekSchedule(userId, week);
        return Result.success(schedules);
    }

    @Operation(summary = "获取课程详情", description = "获取指定课程的详细信息")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "404", description = "课程不存在")
    })
    @GetMapping("/{courseId}")
    public Result<CourseSchedule> getCourseDetail(
            HttpServletRequest request,
            @Parameter(description = "课程 ID", required = true, example = "1")
            @PathVariable Long courseId) {
        Long userId = getCurrentUserId(request);
        CourseSchedule schedule = courseScheduleService.getCourseDetail(userId, courseId);
        return Result.success(schedule);
    }

    @Operation(summary = "复制他人课表", description = "通过分享码复制他人的课表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "复制成功"),
        @ApiResponse(responseCode = "400", description = "分享码无效或课表为空"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @PostMapping("/copy")
    public Result<Void> copySchedule(
            HttpServletRequest request,
            @Parameter(description = "分享码", required = true, example = "SCH260405A1B2")
            @RequestParam(required = false) String shareCode,
            @RequestBody(required = false) Map<String, Object> body) {
        // 支持两种传参方式：URL 参数或 JSON body
        if (shareCode == null && body != null) {
            shareCode = (String) body.get("shareCode");
        }
        if (shareCode == null || shareCode.trim().isEmpty()) {
            return Result.error(400, "分享码不能为空");
        }
        Long userId = getCurrentUserId(request);
        courseScheduleService.copyScheduleByShareCode(userId, shareCode.trim());
        return Result.success();
    }
}