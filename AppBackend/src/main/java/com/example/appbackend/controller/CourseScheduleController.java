package com.example.appbackend.controller;

import com.example.appbackend.dto.ScheduleSettingsDTO;
import com.example.appbackend.dto.ScheduleSettingsUpdateRequest;
import com.example.appbackend.dto.ScheduleSemesterDTO;
import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.ScheduleSemesterSetting;
import com.example.appbackend.entity.User;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.ScheduleSemesterSettingRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.util.WeekCalculator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/schedule")
@Tag(name = "课表管理", description = "课表查询、复制等接口")
public class CourseScheduleController {

    @Autowired
    private CourseScheduleService courseScheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScheduleSemesterSettingRepository scheduleSemesterSettingRepository;

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
    public Result<List<CourseSchedule>> getSchedule(
            HttpServletRequest request,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semesterTerm) {
        Long userId = getCurrentUserId(request);
        SemesterSelection semester = resolveSemester(userId, academicYear, semesterTerm);
        List<CourseSchedule> schedules = courseScheduleService.getUserSchedule(userId, semester.academicYear, semester.semesterTerm);
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
            SemesterSelection semester = resolveSemester(userId, null, null);
            var schedule = courseScheduleService.getCurrentWeekSchedule(
                    userId,
                    semester.semesterStart,
                    semester.academicYear,
                    semester.semesterTerm
            );

            int currentWeek = WeekCalculator.getCurrentWeek(semester.semesterStart);

            Map<String, Object> result = new HashMap<>();
            result.put("currentWeek", currentWeek);
            result.put("count", schedule.size());
            result.put("schedule", schedule);
            result.put("semester", semester.label());
            result.put("academicYear", semester.academicYear);
            result.put("semesterTerm", semester.semesterTerm);
            result.put("semesterCode", semester.semesterCode);
            if (semester.semesterStart != null) {
                result.put("semesterStart", semester.semesterStart.toString());
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取失败：" + e.getMessage());
        }
    }

    @Operation(summary = "获取本周课表", description = "获取当前用户本周的课表")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "401", description = "未登录")
    })
    @GetMapping("/current-week")
    public Result<List<CourseSchedule>> getCurrentWeekSchedule(
            HttpServletRequest request,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semesterTerm) {
        Long userId = getCurrentUserId(request);
        SemesterSelection semester = resolveSemester(userId, academicYear, semesterTerm);
        List<CourseSchedule> schedules = courseScheduleService.getCurrentWeekSchedule(
                userId,
                semester.semesterStart,
                semester.academicYear,
                semester.semesterTerm
        );
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
            @PathVariable Integer week,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semesterTerm) {
        Long userId = getCurrentUserId(request);
        SemesterSelection semester = resolveSemester(userId, academicYear, semesterTerm);
        List<CourseSchedule> schedules = courseScheduleService.getWeekSchedule(userId, week, semester.academicYear, semester.semesterTerm);
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

    @Operation(summary = "获取课表设置", description = "获取当前用户的教务系统账号、密码和学期开始日期")
    @GetMapping("/settings")
    public Result<ScheduleSettingsDTO> getScheduleSettings(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        SemesterSelection selected = resolveSemester(userId, null, null);
        return Result.success(new ScheduleSettingsDTO(
                user.getJwxStudentId(),
                user.getJwxPassword(),
                selected.semesterStart != null ? selected.semesterStart.toString() : "",
                selected.academicYear,
                selected.semesterTerm,
                selected.semesterCode,
                buildSemesterDtos(userId, selected)
        ));
    }

    @Operation(summary = "更新课表设置", description = "更新当前用户的教务系统账号、密码和学期开始日期")
    @PutMapping("/settings")
    @Transactional
    public Result<Void> updateScheduleSettings(
            HttpServletRequest request,
            @Valid @RequestBody ScheduleSettingsUpdateRequest body) {
        Long userId = getCurrentUserId(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));

        if (body.getJwxStudentId() != null) {
            user.setJwxStudentId(body.getJwxStudentId().trim());
        }
        if (body.getJwxPassword() != null) {
            user.setJwxPassword(body.getJwxPassword().trim());
        }

        String academicYear = normalizeAcademicYear(body.getAcademicYear(), user.getSemesterStart());
        Integer semesterTerm = normalizeSemesterTerm(body.getSemesterTerm(), user.getSemesterStart());

        boolean hasSemesterList = body.getSemesters() != null && !body.getSemesters().isEmpty();
        if (body.getSelected() == null || body.getSelected()) {
            scheduleSemesterSettingRepository.clearSelectedByUserId(userId);
        }

        if (hasSemesterList) {
            for (ScheduleSemesterDTO item : body.getSemesters()) {
                if (item == null) {
                    continue;
                }
                String itemAcademicYear = normalizeAcademicYear(item.getAcademicYear() != null ? item.getAcademicYear() : academicYear, user.getSemesterStart());
                Integer itemSemesterTerm = normalizeSemesterTerm(item.getSemesterTerm(), user.getSemesterStart());
                ScheduleSemesterSetting saved = saveSemesterSetting(
                        userId,
                        itemAcademicYear,
                        itemSemesterTerm,
                        item.getSemesterStart(),
                        (body.getSelected() == null || body.getSelected())
                                && Objects.equals(itemAcademicYear, academicYear)
                                && Objects.equals(itemSemesterTerm, semesterTerm)
                );
                if ((body.getSelected() == null || body.getSelected())
                        && Objects.equals(itemAcademicYear, academicYear)
                        && Objects.equals(itemSemesterTerm, semesterTerm)) {
                    user.setSemesterStart(saved.getSemesterStart());
                }
            }
        } else {
            ScheduleSemesterSetting saved = saveSemesterSetting(
                    userId,
                    academicYear,
                    semesterTerm,
                    body.getSemesterStart(),
                    body.getSelected() == null || body.getSelected()
            );
            if (body.getSelected() == null || body.getSelected()) {
                user.setSemesterStart(saved.getSemesterStart());
            }
        }

        userRepository.save(user);
        return Result.success();
    }

    private ScheduleSemesterSetting saveSemesterSetting(
            Long userId,
            String academicYear,
            Integer semesterTerm,
            String semesterStart,
            boolean selected
    ) {
        ScheduleSemesterSetting setting = scheduleSemesterSettingRepository
                .findByUserIdAndAcademicYearAndSemesterTerm(userId, academicYear, semesterTerm)
                .orElseGet(() -> {
                    ScheduleSemesterSetting created = new ScheduleSemesterSetting();
                    created.setUserId(userId);
                    created.setAcademicYear(academicYear);
                    created.setSemesterTerm(semesterTerm);
                    return created;
        });

        setting.setSemesterCode(semesterCodeForTerm(semesterTerm));
        setting.setSemesterStart(parseSemesterStart(semesterStart));
        setting.setSelectedFlag(selected);
        return scheduleSemesterSettingRepository.save(setting);
    }

    private LocalDate parseSemesterStart(String semesterStart) {
        if (semesterStart == null || semesterStart.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(semesterStart.trim());
        } catch (Exception e) {
            throw new BusinessException(400, "学期开始日期格式不正确");
        }
    }

    private List<ScheduleSemesterDTO> buildSemesterDtos(Long userId, SemesterSelection selected) {
        List<ScheduleSemesterDTO> items = new ArrayList<>();
        boolean hasSelected = false;
        for (ScheduleSemesterSetting setting : scheduleSemesterSettingRepository.findByUserIdOrderByAcademicYearDescSemesterTermDesc(userId)) {
            boolean selectedFlag = Objects.equals(setting.getAcademicYear(), selected.academicYear)
                    && Objects.equals(setting.getSemesterTerm(), selected.semesterTerm);
            hasSelected = hasSelected || selectedFlag;
            items.add(toSemesterDto(setting, selectedFlag));
        }
        if (!hasSelected) {
            items.add(new ScheduleSemesterDTO(
                    selected.academicYear,
                    selected.semesterTerm,
                    selected.semesterCode,
                    selected.semesterStart != null ? selected.semesterStart.toString() : "",
                    true,
                    WeekCalculator.getCurrentWeek(selected.semesterStart),
                    courseScheduleService.getUserSchedule(userId, selected.academicYear, selected.semesterTerm).stream().count()
            ));
        }
        return items;
    }

    private ScheduleSemesterDTO toSemesterDto(ScheduleSemesterSetting setting, boolean selected) {
        return new ScheduleSemesterDTO(
                setting.getAcademicYear(),
                setting.getSemesterTerm(),
                setting.getSemesterCode(),
                setting.getSemesterStart() != null ? setting.getSemesterStart().toString() : "",
                selected,
                WeekCalculator.getCurrentWeek(setting.getSemesterStart()),
                courseScheduleService.getUserSchedule(setting.getUserId(), setting.getAcademicYear(), setting.getSemesterTerm()).stream().count()
        );
    }

    private SemesterSelection resolveSemester(Long userId, String requestedAcademicYear, Integer requestedSemesterTerm) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        String academicYear = normalizeAcademicYear(requestedAcademicYear, user.getSemesterStart());
        Integer semesterTerm = normalizeSemesterTerm(requestedSemesterTerm, user.getSemesterStart());

        if ((requestedAcademicYear == null || requestedAcademicYear.trim().isEmpty()) && requestedSemesterTerm == null) {
            var selected = scheduleSemesterSettingRepository.findFirstByUserIdAndSelectedFlagTrue(userId);
            if (selected.isPresent()) {
                ScheduleSemesterSetting setting = selected.get();
                return new SemesterSelection(
                        setting.getAcademicYear(),
                        setting.getSemesterTerm(),
                        setting.getSemesterCode(),
                        setting.getSemesterStart()
                );
            }
        }

        var setting = scheduleSemesterSettingRepository.findByUserIdAndAcademicYearAndSemesterTerm(userId, academicYear, semesterTerm);
        LocalDate start = setting
                .map(ScheduleSemesterSetting::getSemesterStart)
                .orElseGet(() -> user.getSemesterStart() != null
                        ? user.getSemesterStart()
                        : defaultSemesterStart(academicYear, semesterTerm));
        return new SemesterSelection(academicYear, semesterTerm, semesterCodeForTerm(semesterTerm), start);
    }

    private String normalizeAcademicYear(String academicYear, LocalDate fallbackDate) {
        String text = academicYear == null ? "" : academicYear.trim();
        if (text.matches("\\d{4}-\\d{4}")) {
            return text;
        }
        LocalDate date = fallbackDate != null ? fallbackDate : LocalDate.now();
        int year = date.getYear();
        int month = date.getMonthValue();
        int startYear = month >= 8 ? year : year - 1;
        return startYear + "-" + (startYear + 1);
    }

    private Integer normalizeSemesterTerm(Integer semesterTerm, LocalDate fallbackDate) {
        if (semesterTerm != null && (semesterTerm == 1 || semesterTerm == 2)) {
            return semesterTerm;
        }
        LocalDate date = fallbackDate != null ? fallbackDate : LocalDate.now();
        int month = date.getMonthValue();
        return month >= 2 && month <= 7 ? 2 : 1;
    }

    private String semesterCodeForTerm(Integer semesterTerm) {
        return Integer.valueOf(2).equals(semesterTerm) ? "12" : "3";
    }

    private int academicYearStart(String academicYear) {
        if (academicYear != null && academicYear.matches("\\d{4}-\\d{4}")) {
            return Integer.parseInt(academicYear.substring(0, 4));
        }
        return LocalDate.now().getYear();
    }

    private LocalDate defaultSemesterStart(String academicYear, Integer semesterTerm) {
        int startYear = academicYearStart(academicYear);
        if (Integer.valueOf(2).equals(semesterTerm)) {
            return LocalDate.of(startYear + 1, 3, 1);
        }
        return LocalDate.of(startYear, 9, 1);
    }

    private static class SemesterSelection {
        private final String academicYear;
        private final Integer semesterTerm;
        private final String semesterCode;
        private final LocalDate semesterStart;

        private SemesterSelection(String academicYear, Integer semesterTerm, String semesterCode, LocalDate semesterStart) {
            this.academicYear = academicYear;
            this.semesterTerm = semesterTerm;
            this.semesterCode = semesterCode;
            this.semesterStart = semesterStart;
        }

        private String label() {
            return academicYear + " 第 " + semesterTerm + " 学期";
        }
    }
}
