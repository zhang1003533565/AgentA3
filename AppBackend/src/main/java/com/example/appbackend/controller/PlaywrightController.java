package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.service.PlaywrightService;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.util.WeekCalculator;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/browser")
public class PlaywrightController {

    private final PlaywrightService playwrightService;
    private final CourseScheduleService courseScheduleService;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;

    public PlaywrightController(PlaywrightService playwrightService,
                                CourseScheduleService courseScheduleService,
                                UserRepository userRepository,
                                SystemConfigService systemConfigService) {
        this.playwrightService = playwrightService;
        this.courseScheduleService = courseScheduleService;
        this.userRepository = userRepository;
        this.systemConfigService = systemConfigService;
    }

    /**
     * 自动获取课表（从 Token 中获取用户信息，自动爬取并保存）
     */
    @PostMapping("/jwx/schedule/auto")
    public Result<Map<String, Object>> autoGetSchedule(HttpServletRequest request) {
        // 从请求属性中获取用户 ID（由 JwtInterceptor 设置）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或 Token 无效");
        }

        // 从数据库获取用户的教务系统账号密码
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String jwxStudentId = user.getJwxStudentId();
        String jwxPassword = user.getJwxPassword();

        if (jwxStudentId == null || jwxStudentId.isEmpty() ||
            jwxPassword == null || jwxPassword.isEmpty()) {
            return Result.error("请先绑定教务系统账号和密码");
        }

        BrowserContext context = null;
        try {
            boolean headless = systemConfigService.getBooleanValue("browser.headless", true);
            String defaultUrl = systemConfigService.getValue("browser.default-url", "https://jwx.hebiace.edu.cn/");
            context = playwrightService.createBrowserContext(headless);
            Page page = playwrightService.navigate(context, defaultUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            Thread.sleep(500);
            playwrightService.fill(page, "#yhm", jwxStudentId);
            playwrightService.fill(page, "#mm", jwxPassword);
            playwrightService.click(page, "#dl");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(2000);

            // 点击下拉框：信息查询
            playwrightService.click(page, "a.dropdown-toggle:has-text('信息查询')");
            Thread.sleep(500);

            // 使用 context.waitForPage 等待新页面打开
            Page newPage = context.waitForPage(() -> {
                // 点击个人课表查询
                playwrightService.evaluate(page,
                    "Array.from(document.querySelectorAll('a')).find(a => a.textContent.includes('个人课表查询'))?.click()"
                );
            });

            if (newPage == null) {
                return Result.error("未能打开课表页面");
            }

            newPage.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(2000);

            // 获取所有课表块，每个 div 单独作为一个数组元素
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> courseBlocks = (List<Map<String, Object>>) playwrightService.evaluate(newPage,
                "() => { " +
                "  function cleanText(text) { " +
                "    return (text || '').replace(/\\s+/g, ' ').trim(); " +
                "  } " +
                "  function parsePeriodWeek(text) { " +
                "    const raw = cleanText(text); " +
                "    const result = { sectionText: '', sectionStart: null, sectionEnd: null, weekText: '' }; " +
                "    if (!raw) return result; " +
                "    const normalized = raw.replace(/\\s+/g, ''); " +
                "    const sectionMatch = normalized.match(/\\(([^)]+)\\)/); " +
                "    if (sectionMatch) { " +
                "      result.sectionText = sectionMatch[1]; " +
                "      const secMatch = sectionMatch[1].match(/(\\d+)-(\\d+) 节/); " +
                "      if (secMatch) { " +
                "        result.sectionStart = Number(secMatch[1]); " +
                "        result.sectionEnd = Number(secMatch[2]); " +
                "      } " +
                "    } " +
                "    const weekPart = normalized.replace(/\\([^)]+\\)/, ''); " +
                "    result.weekText = weekPart; " +
                "    return result; " +
                "  } " +
                "  function extractRawFields(courseDiv) { " +
                "    const rawFields = {}; " +
                "    const rows = courseDiv.querySelectorAll('p'); " +
                "    rows.forEach((p) => { " +
                "      const labelEl = p.querySelector('span[title]'); " +
                "      if (!labelEl) return; " +
                "      const key = cleanText(labelEl.getAttribute('title')); " +
                "      if (!key) return; " +
                "      const value = cleanText(p.textContent); " +
                "      rawFields[key] = value; " +
                "    }); " +
                "    return rawFields; " +
                "  } " +
                "  function extractCourseDiv(courseDiv, index) { " +
                "    const rawFields = extractRawFields(courseDiv); " +
                "    const courseName = cleanText(courseDiv.querySelector('.title')?.textContent || ''); " +
                "    const periodInfo = parsePeriodWeek(rawFields['节/周'] || ''); " +
                "    const parentTd = courseDiv.parentElement; " +
                "    const tdId = parentTd?.id || ''; " +
                "    let weekday = null; " +
                "    if (tdId) { " +
                "      const weekdayMatch = tdId.match(/^(\\d)-/); " +
                "      if (weekdayMatch) { " +
                "        weekday = Number(weekdayMatch[1]); " +
                "      } " +
                "    } " +
                "    return { " +
                "      index: index, " +
                "      courseName: courseName, " +
                "      sections: periodInfo.sectionText, " +
                "      sectionStart: periodInfo.sectionStart, " +
                "      sectionEnd: periodInfo.sectionEnd, " +
                "      weekText: periodInfo.weekText, " +
                "      weekday: weekday, " +
                "      location: rawFields['上课地点'] || '', " +
                "      teacher: rawFields['教师'] || '', " +
                "      classCode: rawFields['教学班名称'] || '', " +
                "      classComposition: rawFields['教学班组成'] || '', " +
                "      assessmentType: rawFields['考核方式'] || '', " +
                "      remark: rawFields['选课备注'] || '', " +
                "      hourComposition: rawFields['课程学时组成'] || '', " +
                "      weeklyHours: rawFields['周学时'] || '', " +
                "      totalHours: rawFields['总学时'] || '', " +
                "      credit: rawFields['学分'] || '', " +
                "      rawFields: rawFields " +
                "    }; " +
                "  } " +
                "  function extractAllCourses(selector) { " +
                "    const divs = Array.from(document.querySelectorAll(selector)); " +
                "    return divs.map((div, index) => extractCourseDiv(div, index)); " +
                "  } " +
                "  const courses = extractAllCourses('.timetable_con'); " +
                "  console.log('提取到的课程数据：', courses.length); " +
                "  return courses; " +
                "}"
            );

            // 构建 rawData 格式用于解析保存
            StringBuilder rawDataBuilder = new StringBuilder();
            for (Map<String, Object> course : courseBlocks) {
                rawDataBuilder.append("=== 课表块 ").append(course.get("index")).append(" ===\n");
                rawDataBuilder.append("\"courseName\": \"").append(course.get("courseName")).append("\"\n");
                rawDataBuilder.append("\"sections\": \"").append(course.get("sections")).append("\"\n");
                rawDataBuilder.append("\"sectionStart\": ").append(course.get("sectionStart")).append("\n");
                rawDataBuilder.append("\"sectionEnd\": ").append(course.get("sectionEnd")).append("\n");
                rawDataBuilder.append("\"weekText\": \"").append(course.get("weekText")).append("\"\n");
                Object weekdayVal = course.get("weekday");
                if (weekdayVal != null) {
                    rawDataBuilder.append("\"weekday\": ").append(weekdayVal).append("\n");
                } else {
                    rawDataBuilder.append("\"weekday\": null\n");
                }
                rawDataBuilder.append("\"location\": \"").append(course.get("location")).append("\"\n");
                rawDataBuilder.append("\"teacher\": \"").append(course.get("teacher")).append("\"\n");
                rawDataBuilder.append("\"classCode\": \"").append(course.get("classCode")).append("\"\n");
                rawDataBuilder.append("\"classComposition\": \"").append(course.get("classComposition")).append("\"\n");
                rawDataBuilder.append("\"assessmentType\": \"").append(course.get("assessmentType")).append("\"\n");
                rawDataBuilder.append("\"hourComposition\": \"").append(course.get("hourComposition")).append("\"\n");
                rawDataBuilder.append("\"weeklyHours\": \"").append(course.get("weeklyHours")).append("\"\n");
                rawDataBuilder.append("\"totalHours\": \"").append(course.get("totalHours")).append("\"\n");
                rawDataBuilder.append("\"credit\": \"").append(course.get("credit")).append("\"\n");
            }
            String rawData = rawDataBuilder.toString();

            // 调试：打印第一个课表块的节次信息
            if (!courseBlocks.isEmpty()) {
                var firstBlock = courseBlocks.get(0);
                System.out.println("DEBUG - courseName: " + firstBlock.get("courseName"));
                System.out.println("DEBUG - sections: " + firstBlock.get("sections"));
                System.out.println("DEBUG - sectionStart: " + firstBlock.get("sectionStart"));
                System.out.println("DEBUG - sectionEnd: " + firstBlock.get("sectionEnd"));
            }

            // 保存课表数据到数据库
            courseScheduleService.saveSchedule(user.getId(), jwxStudentId, rawData);

            // 获取保存后的课表
            var savedSchedule = courseScheduleService.getUserSchedule(user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", savedSchedule.size());
            result.put("schedule", savedSchedule);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("操作失败：" + e.getMessage());
        } finally {
            playwrightService.closeBrowser(context);
        }
    }

    /**
     * 教务系统登录并获取课表数据（保存到数据库）
     */
    @PostMapping("/jwx/schedule")
    public Result<Map<String, Object>> getSchedule(@RequestBody JwxLoginRequest request) {
        BrowserContext context = null;
        try {
            boolean headless = systemConfigService.getBooleanValue("browser.headless", true);
            String defaultUrl = systemConfigService.getValue("browser.default-url", "https://jwx.hebiace.edu.cn/");
            context = playwrightService.createBrowserContext(headless);
            Page page = playwrightService.navigate(context, defaultUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            Thread.sleep(500);
            playwrightService.fill(page, "#yhm", request.getUsername());
            playwrightService.fill(page, "#mm", request.getPassword());
            playwrightService.click(page, "#dl");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(2000);

            // 点击下拉框：信息查询
            playwrightService.click(page, "a.dropdown-toggle:has-text('信息查询')");
            Thread.sleep(500);

            // 使用 context.waitForPage 等待新页面打开
            Page newPage = context.waitForPage(() -> {
                // 点击个人课表查询
                playwrightService.evaluate(page,
                    "Array.from(document.querySelectorAll('a')).find(a => a.textContent.includes('个人课表查询'))?.click()"
                );
            });

            if (newPage == null) {
                return Result.error("未能打开课表页面");
            }

            newPage.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(2000);

            // 获取所有课表块（包含节次信息）
            String rawData = (String) playwrightService.evaluate(newPage,
                "() => { " +
                "  function cleanText(text) { " +
                "    return (text || '').replace(/\\s+/g, ' ').trim(); " +
                "  } " +
                "  function parsePeriodWeek(text) { " +
                "    const raw = cleanText(text); " +
                "    const result = { sectionText: '', sectionStart: null, sectionEnd: null, weekText: '' }; " +
                "    if (!raw) return result; " +
                "    const normalized = raw.replace(/\\s+/g, ''); " +
                "    const sectionMatch = normalized.match(/\\(([^)]+)\\)/); " +
                "    if (sectionMatch) { " +
                "      result.sectionText = sectionMatch[1]; " +
                "      const secMatch = sectionMatch[1].match(/(\\d+)-(\\d+) 节/); " +
                "      if (secMatch) { " +
                "        result.sectionStart = Number(secMatch[1]); " +
                "        result.sectionEnd = Number(secMatch[2]); " +
                "      } " +
                "    } " +
                "    const weekPart = normalized.replace(/\\([^)]+\\)/, ''); " +
                "    result.weekText = weekPart; " +
                "    return result; " +
                "  } " +
                "  function extractRawFields(courseDiv) { " +
                "    const rawFields = {}; " +
                "    const rows = courseDiv.querySelectorAll('p'); " +
                "    rows.forEach((p) => { " +
                "      const labelEl = p.querySelector('span[title]'); " +
                "      if (!labelEl) return; " +
                "      const key = cleanText(labelEl.getAttribute('title')); " +
                "      if (!key) return; " +
                "      const value = cleanText(p.textContent); " +
                "      rawFields[key] = value; " +
                "    }); " +
                "    return rawFields; " +
                "  } " +
                "  function extractCourseDiv(courseDiv, index) { " +
                "    const rawFields = extractRawFields(courseDiv); " +
                "    const courseName = cleanText(courseDiv.querySelector('.title')?.textContent || ''); " +
                "    const periodInfo = parsePeriodWeek(rawFields['节/周'] || ''); " +
                "    const parentTd = courseDiv.parentElement; " +
                "    const tdId = parentTd?.id || ''; " +
                "    let weekday = null; " +
                "    if (tdId) { " +
                "      const weekdayMatch = tdId.match(/^(\\d)-/); " +
                "      if (weekdayMatch) { " +
                "        weekday = Number(weekdayMatch[1]); " +
                "      } " +
                "    } " +
                "    return { " +
                "      index: index, " +
                "      courseName: courseName, " +
                "      sections: periodInfo.sectionText, " +
                "      sectionStart: periodInfo.sectionStart, " +
                "      sectionEnd: periodInfo.sectionEnd, " +
                "      weekText: periodInfo.weekText, " +
                "      weekday: weekday, " +
                "      location: rawFields['上课地点'] || '', " +
                "      teacher: rawFields['教师'] || '', " +
                "      classCode: rawFields['教学班名称'] || '', " +
                "      classComposition: rawFields['教学班组成'] || '', " +
                "      assessmentType: rawFields['考核方式'] || '', " +
                "      hourComposition: rawFields['课程学时组成'] || '', " +
                "      weeklyHours: rawFields['周学时'] || '', " +
                "      totalHours: rawFields['总学时'] || '', " +
                "      credit: rawFields['学分'] || '' " +
                "    }; " +
                "  } " +
                "  function extractAllCourses(selector) { " +
                "    const divs = Array.from(document.querySelectorAll(selector)); " +
                "    return divs.map((div, index) => extractCourseDiv(div, index)); " +
                "  } " +
                "  const courses = extractAllCourses('.timetable_con'); " +
                "  let output = []; " +
                "  courses.forEach(function(course) { " +
                "    output.push('=== 课表块 ' + (course.index + 1) + ' ==='); " +
                "    output.push('\"courseName\": \"' + course.courseName + '\"'); " +
                "    output.push('\"sections\": \"' + course.sections + '\"'); " +
                "    output.push('\"sectionStart\": ' + course.sectionStart); " +
                "    output.push('\"sectionEnd\": ' + course.sectionEnd); " +
                "    output.push('\"weekText\": \"' + course.weekText + '\"'); " +
                "    output.push('\"weekday\": ' + course.weekday); " +
                "    output.push('\"location\": \"' + course.location + '\"'); " +
                "    output.push('\"teacher\": \"' + course.teacher + '\"'); " +
                "    output.push('\"classCode\": \"' + course.classCode + '\"'); " +
                "    output.push('\"classComposition\": \"' + course.classComposition + '\"'); " +
                "    output.push('\"assessmentType\": \"' + course.assessmentType + '\"'); " +
                "    output.push('\"hourComposition\": \"' + course.hourComposition + '\"'); " +
                "    output.push('\"weeklyHours\": \"' + course.weeklyHours + '\"'); " +
                "    output.push('\"totalHours\": \"' + course.totalHours + '\"'); " +
                "    output.push('\"credit\": \"' + course.credit + '\"'); " +
                "    output.push(''); " +
                "  }); " +
                "  return output.join('\\n'); " +
                "}"
            );

            // 根据学号查找用户
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            // 保存课表数据到数据库
            courseScheduleService.saveSchedule(user.getId(), request.getUsername(), rawData);

            // 获取保存后的课表
            var savedSchedule = courseScheduleService.getUserSchedule(user.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "课表已保存，共 " + savedSchedule.size() + " 门课程");
            result.put("count", savedSchedule.size());
            result.put("schedule", savedSchedule);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("操作失败：" + e.getMessage());
        } finally {
            playwrightService.closeBrowser(context);
        }
    }

    /**
     * 获取用户的课表（从数据库）
     */
    @GetMapping("/jwx/schedule")
    public Result<Map<String, Object>> getUserSchedule(@RequestParam Long userId) {
        try {
            var schedule = courseScheduleService.getUserSchedule(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", schedule.size());
            result.put("schedule", schedule);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户本周的课表（根据学期开始日期自动计算当前周次）
     */
    @GetMapping("/jwx/schedule/current")
    public Result<Map<String, Object>> getCurrentWeekSchedule(HttpServletRequest request) {
        try {
            // 从请求属性中获取用户 ID（由 JwtInterceptor 设置）
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error("未登录或 Token 无效");
            }

            var schedule = courseScheduleService.getCurrentWeekSchedule(userId);

            // 计算当前周次用于返回给前端
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            int currentWeek = WeekCalculator.getCurrentWeek(user.getSemesterStart());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("currentWeek", currentWeek);
            result.put("count", schedule.size());
            result.put("schedule", schedule);

            // 计算学期信息
            String semester = calculateSemester(user.getSemesterStart());
            result.put("semester", semester);

            // 返回学期开始日期给前端
            if (user.getSemesterStart() != null) {
                result.put("semesterStart", user.getSemesterStart().toString());
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取失败：" + e.getMessage());
        }
    }

    /**
     * 获取用户指定周次的课表
     */
    @GetMapping("/jwx/schedule/week/{week}")
    public Result<Map<String, Object>> getWeekSchedule(
            @PathVariable Integer week,
            HttpServletRequest request) {
        try {
            // 从请求属性中获取用户 ID（由 JwtInterceptor 设置）
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error("未登录或 Token 无效");
            }

            // 验证周次范围
            if (week == null || week < 1 || week > 20) {
                return Result.error("周次必须在 1-20 之间");
            }

            var schedule = courseScheduleService.getWeekSchedule(userId, week);

            // 获取用户信息用于计算学期
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("currentWeek", week);
            result.put("count", schedule.size());
            result.put("schedule", schedule);

            // 计算学期信息
            String semester = calculateSemester(user.getSemesterStart());
            result.put("semester", semester);

            // 返回学期开始日期给前端
            if (user.getSemesterStart() != null) {
                result.put("semesterStart", user.getSemesterStart().toString());
            }

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取失败：" + e.getMessage());
        }
    }

    /**
     * 获取课程详情
     */
    @GetMapping("/jwx/schedule/{courseId}")
    public Result<Map<String, Object>> getCourseDetail(
            @PathVariable Long courseId,
            HttpServletRequest request) {
        try {
            // 从请求属性中获取用户 ID（由 JwtInterceptor 设置）
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error("未登录或 Token 无效");
            }

            CourseSchedule schedule = courseScheduleService.getCourseDetail(userId, courseId);

            // 计算学期信息
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            String semester = calculateSemester(user.getSemesterStart());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("course", schedule);
            result.put("semester", semester);

            return Result.success(result);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("获取失败：" + e.getMessage());
        }
    }

    /**
     * 直接保存课表数据（传入解析后的数据）
     */
    @PostMapping("/jwx/schedule/save")
    public Result<Map<String, Object>> saveSchedule(@RequestBody SaveScheduleRequest request) {
        try {
            // 直接使用工具类解析数据
            var schedules = com.example.appbackend.util.CourseScheduleParser.parse(
                request.getRawData(), request.getUserId(), request.getStudentId()
            );

            // 保存课表数据到数据库
            courseScheduleService.saveSchedule(request.getUserId(), request.getStudentId(), request.getRawData());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "课表已保存，共 " + schedules.size() + " 门课程");
            result.put("count", schedules.size());
            result.put("schedule", schedules);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("保存失败：" + e.getMessage());
        }
    }

    /**
     * 根据学期开始日期和当前周次计算学期名称
     * 假设：2-7 月为春季学期（第 2 学期），8-1 月为秋季学期（第 1 学期）
     */
    private String calculateSemester(java.time.LocalDate semesterStart) {
        if (semesterStart == null) {
            return "2025-2026 第 2 学期";
        }

        int startYear = semesterStart.getYear();
        int startMonth = semesterStart.getMonthValue();

        // 2-7 月为春季学期（第 2 学期），8-1 月为秋季学期（第 1 学期）
        int semesterNum = (startMonth >= 2 && startMonth <= 7) ? 2 : 1;

        // 学年计算：春季学期属于上一学年，秋季学期属于当前学年
        int academicYearStart = semesterNum == 2 ? startYear - 1 : startYear;
        int academicYearEnd = academicYearStart + 1;

        return academicYearStart + "-" + academicYearEnd + " 第 " + semesterNum + " 学期";
    }

    /**
     * 检查用户是否绑定了教务系统账号
     */
    @GetMapping("/jwx/user/check-jwx-bind")
    public Result<Map<String, Object>> checkJwxBind(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error("未登录或 Token 无效");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            boolean binded = user.getJwxStudentId() != null && !user.getJwxStudentId().isEmpty()
                          && user.getJwxPassword() != null && !user.getJwxPassword().isEmpty();

            Map<String, Object> result = new HashMap<>();
            result.put("binded", binded);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("检查失败：" + e.getMessage());
        }
    }

    // --- 请求 DTO ---

    @lombok.Data
    public static class JwxLoginRequest {
        private String username;  // 学号
        private String password;  // 密码
    }

    @lombok.Data
    public static class SaveScheduleRequest {
        private Long userId;        // 用户 ID
        private String studentId;   // 学号
        private String rawData;     // 课表原始数据
    }
}
