package com.example.appbackend.controller;

import com.example.appbackend.dto.ScheduleChangeSummary;
import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.ScheduleSemesterSetting;
import com.example.appbackend.entity.User;
import com.example.appbackend.entity.CourseSchedule;
import com.example.appbackend.repository.ScheduleSemesterSettingRepository;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.service.PlaywrightService;
import com.example.appbackend.service.SystemConfigService;
import com.example.appbackend.util.WeekCalculator;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/browser")
public class PlaywrightController {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightController.class);
    private static final Map<Long, Map<String, Object>> IMPORT_PROGRESS = new ConcurrentHashMap<>();

    private final PlaywrightService playwrightService;
    private final CourseScheduleService courseScheduleService;
    private final UserRepository userRepository;
    private final ScheduleSemesterSettingRepository scheduleSemesterSettingRepository;
    private final SystemConfigService systemConfigService;

    public PlaywrightController(PlaywrightService playwrightService,
                                CourseScheduleService courseScheduleService,
                                UserRepository userRepository,
                                ScheduleSemesterSettingRepository scheduleSemesterSettingRepository,
                                SystemConfigService systemConfigService) {
        this.playwrightService = playwrightService;
        this.courseScheduleService = courseScheduleService;
        this.userRepository = userRepository;
        this.scheduleSemesterSettingRepository = scheduleSemesterSettingRepository;
        this.systemConfigService = systemConfigService;
    }

    @GetMapping("/jwx/schedule/import-progress")
    public Result<Map<String, Object>> getScheduleImportProgress(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error("未登录或 Token 无效");
        }
        Map<String, Object> progress = IMPORT_PROGRESS.get(userId);
        if (progress == null) {
            progress = new HashMap<>();
            progress.put("status", "idle");
            progress.put("step", "prepare");
            progress.put("message", "暂无导入任务");
            progress.put("percent", 0);
        }
        return Result.success(progress);
    }

    private void updateImportProgress(Long userId, String status, String step, String message, int percent) {
        if (userId == null) {
            return;
        }
        Map<String, Object> progress = new HashMap<>();
        progress.put("status", status);
        progress.put("step", step);
        progress.put("message", message);
        progress.put("percent", percent);
        progress.put("updatedAt", System.currentTimeMillis());
        IMPORT_PROGRESS.put(userId, progress);
    }

    /**
     * 自动获取课表（从 Token 中获取用户信息，自动爬取并保存）
     */
    @PostMapping("/jwx/schedule/auto")
    @Transactional
    public Result<Map<String, Object>> autoGetSchedule(
            @RequestBody(required = false) ScheduleImportRequest importRequest,
            HttpServletRequest request) {
        // 从请求属性中获取用户 ID（由 JwtInterceptor 设置）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            log.warn("课表自动导入失败：未登录或 token 无效");
            return Result.error("未登录或 Token 无效");
        }
        updateImportProgress(userId, "running", "prepare", "正在读取教务账号配置", 5);
        log.info("课表自动导入开始，userId={}", userId);

        // 从数据库获取用户的教务系统账号密码
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String jwxStudentId = user.getJwxStudentId();
        String jwxPassword = user.getJwxPassword();
        String academicYear = normalizeAcademicYear(importRequest == null ? null : importRequest.getAcademicYear(), user.getSemesterStart());
        Integer selectedSemesterTerm = normalizeSemesterTerm(importRequest == null ? null : importRequest.getSelectedSemesterTerm(), user.getSemesterStart());
        boolean importBothTerms = importRequest == null || importRequest.getImportBothTerms() == null || importRequest.getImportBothTerms();
        List<Integer> terms = importBothTerms ? List.of(1, 2) : List.of(selectedSemesterTerm);
        log.info("读取课表绑定信息，userId={}, jwxConfigured={}, academicYear={}, terms={}, semesterStart={}",
                userId,
                jwxStudentId != null && !jwxStudentId.isEmpty() && jwxPassword != null && !jwxPassword.isEmpty(),
                academicYear,
                terms,
                user.getSemesterStart());

        if (jwxStudentId == null || jwxStudentId.isEmpty() ||
            jwxPassword == null || jwxPassword.isEmpty()) {
            log.warn("课表自动导入失败：教务账号或密码未绑定，userId={}", userId);
            updateImportProgress(userId, "failed", "prepare", "请先设置教务系统账号和密码", 0);
            return Result.error("请先绑定教务系统账号和密码");
        }

        BrowserContext context = null;
        try {
            updateImportProgress(userId, "running", "connect", "正在连接教务系统", 15);
            boolean headless = systemConfigService.getBooleanValue("browser.headless", true);
            String defaultUrl = systemConfigService.getValue("browser.default-url", "https://jwx.hebiace.edu.cn/");
            context = playwrightService.createBrowserContext(headless);
            Page page = playwrightService.navigate(context, defaultUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            updateImportProgress(userId, "running", "login", "正在登录教务系统账号", 30);
            Thread.sleep(500);
            playwrightService.fill(page, "#yhm", jwxStudentId);
            playwrightService.fill(page, "#mm", jwxPassword);
            playwrightService.click(page, "#dl");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(2000);
            updateImportProgress(userId, "running", "login", "登录账号成功", 42);

            // 点击下拉框：信息查询
            updateImportProgress(userId, "running", "query", "正在打开个人课表查询", 52);
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
                updateImportProgress(userId, "failed", "query", "未能打开课表页面", 52);
                return Result.error("未能打开课表页面");
            }

            newPage.waitForLoadState(LoadState.NETWORKIDLE);
            Thread.sleep(2000);
            updateImportProgress(userId, "running", "read", "课表页面加载完成，正在读取课程", 62);
            log.info("教务系统课表页面加载完成，userId={}, currentUrl={}", userId, newPage.url());

            scheduleSemesterSettingRepository.clearSelectedByUserId(userId);
            int totalCount = 0;
            LocalDate selectedSemesterStart = null;
            List<Map<String, Object>> semesterResults = new ArrayList<>();
            for (Integer semesterTerm : terms) {
                String semesterCode = semesterCodeForTerm(semesterTerm);
                updateImportProgress(userId, "running", "read", "正在读取第 " + semesterTerm + " 学期课表", 72);
                Map<String, Object> payload = fetchJwxSchedulePayload(newPage, academicYearStart(academicYear), semesterCode);
                String rawData = buildRawDataFromJwxPayload(payload);
                LocalDate semesterStart = resolveImportSemesterStart(userId, importRequest, semesterTerm, academicYear, user.getSemesterStart());
                boolean selected = semesterTerm.equals(selectedSemesterTerm);
                upsertSemesterSetting(userId, academicYear, semesterTerm, semesterCode, semesterStart, selected);
                if (selected) {
                    selectedSemesterStart = semesterStart;
                }

                updateImportProgress(userId, "running", "save", "正在保存第 " + semesterTerm + " 学期课程", 88);
                ScheduleChangeSummary changes = courseScheduleService.saveScheduleAndSummarizeChanges(
                        user.getId(),
                        jwxStudentId,
                        rawData,
                        academicYear,
                        semesterTerm,
                        semesterCode
                );
                var savedSchedule = courseScheduleService.getUserSchedule(user.getId(), academicYear, semesterTerm);
                totalCount += savedSchedule.size();

                Map<String, Object> item = new HashMap<>();
                item.put("academicYear", academicYear);
                item.put("semesterTerm", semesterTerm);
                item.put("semesterCode", semesterCode);
                item.put("semesterStart", semesterStart != null ? semesterStart.toString() : "");
                item.put("count", savedSchedule.size());
                item.put("changes", changes);
                semesterResults.add(item);
                log.info("课表学期导入完成，userId={}, academicYear={}, semesterTerm={}, count={}",
                        userId, academicYear, semesterTerm, savedSchedule.size());
            }
            if (selectedSemesterStart != null) {
                user.setSemesterStart(selectedSemesterStart);
                userRepository.save(user);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("academicYear", academicYear);
            result.put("count", totalCount);
            result.put("semesters", semesterResults);
            if (semesterResults.size() == 1) {
                result.put("changes", semesterResults.get(0).get("changes"));
            }
            result.put("message", "课表已导入，共 " + totalCount + " 门课程");

            updateImportProgress(userId, "done", "done", "导入完成，共 " + totalCount + " 门课程", 100);
            return Result.success(result);
        } catch (Exception e) {
            log.error("课表自动导入异常，userId={}, message={}", userId, e.getMessage(), e);
            updateImportProgress(userId, "failed", "failed", "导入失败：" + e.getMessage(), 0);
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
    public Result<Map<String, Object>> getCurrentWeekSchedule(
            HttpServletRequest request,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semesterTerm) {
        try {
            // 从请求属性中获取用户 ID（由 JwtInterceptor 设置）
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                log.warn("获取当前周课表失败：未登录或 token 无效");
                return Result.error("未登录或 Token 无效");
            }

            SemesterSelection semester = resolveSemester(userId, academicYear, semesterTerm);
            var schedule = courseScheduleService.getCurrentWeekSchedule(
                    userId,
                    semester.semesterStart,
                    semester.academicYear,
                    semester.semesterTerm
            );

            int currentWeek = WeekCalculator.getCurrentWeek(semester.semesterStart);
            log.info("获取当前周课表，userId={}, academicYear={}, semesterTerm={}, semesterStart={}, currentWeek={}, courseCount={}",
                    userId, semester.academicYear, semester.semesterTerm, semester.semesterStart, currentWeek, schedule.size());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
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

    /**
     * 获取用户指定周次的课表
     */
    @GetMapping("/jwx/schedule/week/{week}")
    public Result<Map<String, Object>> getWeekSchedule(
            @PathVariable Integer week,
            HttpServletRequest request,
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semesterTerm) {
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

            SemesterSelection semester = resolveSemester(userId, academicYear, semesterTerm);
            var schedule = courseScheduleService.getWeekSchedule(userId, week, semester.academicYear, semester.semesterTerm);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("currentWeek", week);
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

    private Map<String, Object> fetchJwxSchedulePayload(Page page, int academicYearStart, String semesterCode) {
        String script = "() => (async () => { " +
                "const body = new URLSearchParams(); " +
                "body.append('xnm', '" + academicYearStart + "'); " +
                "body.append('xqm', '" + cleanRawValue(semesterCode) + "'); " +
                "body.append('kzlx', 'ck'); " +
                "body.append('xsdm', ''); " +
                "body.append('kclbdm', ''); " +
                "body.append('kclxdm', ''); " +
                "const response = await fetch('/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508', { " +
                "method: 'POST', " +
                "credentials: 'same-origin', " +
                "headers: { " +
                "'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', " +
                "'X-Requested-With': 'XMLHttpRequest' " +
                "}, " +
                "body: body.toString() " +
                "}); " +
                "const text = await response.text(); " +
                "try { return JSON.parse(text); } catch (e) { return { success: false, raw: text }; } " +
                "})()";
        Object result = playwrightService.evaluate(page, script);
        if (!(result instanceof Map<?, ?> resultMap)) {
            throw new RuntimeException("教务系统课表接口返回格式异常");
        }

        Map<String, Object> payload = new HashMap<>();
        resultMap.forEach((key, value) -> payload.put(String.valueOf(key), value));
        if (!payload.containsKey("kbList") && payload.containsKey("raw")) {
            throw new RuntimeException("教务系统课表接口返回非 JSON 数据");
        }
        return payload;
    }

    private String buildRawDataFromJwxPayload(Map<String, Object> payload) {
        Object kbList = payload.get("kbList");
        if (!(kbList instanceof List<?> courses) || courses.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        int index = 1;
        for (Object item : courses) {
            if (!(item instanceof Map<?, ?> course)) {
                continue;
            }
            String courseName = firstNonBlank(course, "kcmc");
            if (courseName.isEmpty()) {
                continue;
            }

            output.append("=== 课表块 ").append(index++).append(" ===\n");
            appendRawField(output, "courseName", courseName);
            appendRawField(output, "sections", firstNonBlank(course, "jc", "jcs", "jcor"));
            appendRawValue(output, "sectionStart", parseSectionPart(firstNonBlank(course, "jcs", "jcor", "jc"), 0));
            appendRawValue(output, "sectionEnd", parseSectionPart(firstNonBlank(course, "jcs", "jcor", "jc"), 1));
            appendRawField(output, "weekText", firstNonBlank(course, "zcd", "qsjsz"));
            appendRawValue(output, "weekday", parseIntOrNull(firstNonBlank(course, "xqj")));
            appendRawField(output, "location", firstNonBlank(course, "cdmc", "lh"));
            appendRawField(output, "teacher", firstNonBlank(course, "xm", "jsxm"));
            appendRawField(output, "classCode", firstNonBlank(course, "jxbmc", "jxb_id"));
            appendRawField(output, "classComposition", firstNonBlank(course, "jxbzc", "jxbzh"));
            appendRawField(output, "assessmentType", firstNonBlank(course, "khfsmc"));
            appendRawField(output, "hourComposition", firstNonBlank(course, "kcxszc"));
            appendRawField(output, "weeklyHours", firstNonBlank(course, "zhxs"));
            appendRawField(output, "totalHours", firstNonBlank(course, "zxs", "kczxs"));
            appendRawField(output, "credit", firstNonBlank(course, "xf"));
            output.append('\n');
        }
        return output.toString();
    }

    private void appendRawField(StringBuilder output, String fieldName, String value) {
        output.append('"')
                .append(fieldName)
                .append("\": \"")
                .append(cleanRawValue(value))
                .append("\"\n");
    }

    private void appendRawValue(StringBuilder output, String fieldName, Integer value) {
        output.append('"')
                .append(fieldName)
                .append("\": ")
                .append(value == null ? "null" : value)
                .append('\n');
    }

    private String firstNonBlank(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            String value = stringValue(source.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String firstNonBlankValue(String... values) {
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).replaceAll("\\s+", " ").trim();
    }

    private String cleanRawValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('"', '\'')
                .trim();
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            String digits = value.replaceAll("[^0-9-]", "");
            return digits.isEmpty() ? null : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseSectionPart(String sections, int partIndex) {
        if (sections == null || sections.trim().isEmpty()) {
            return null;
        }
        String[] parts = sections.replace("节", "").split("-");
        if (partIndex >= parts.length) {
            return parseIntOrNull(parts[0]);
        }
        return parseIntOrNull(parts[partIndex]);
    }

    private LocalDate resolveImportSemesterStart(
            Long userId,
            ScheduleImportRequest importRequest,
            Integer semesterTerm,
            String academicYear,
            LocalDate fallbackDate) {
        String configured = "";
        if (importRequest != null && importRequest.getSemesterStarts() != null) {
            configured = firstNonBlankValue(
                    importRequest.getSemesterStarts().get(String.valueOf(semesterTerm)),
                    importRequest.getSemesterStarts().get("term" + semesterTerm),
                    importRequest.getSemesterStarts().get("semester" + semesterTerm)
            );
        }
        LocalDate parsed = parseDateOrNull(configured);
        if (parsed != null) {
            return parsed;
        }

        var existing = scheduleSemesterSettingRepository.findByUserIdAndAcademicYearAndSemesterTerm(
                userId,
                academicYear,
                semesterTerm
        );
        if (existing.isPresent() && existing.get().getSemesterStart() != null) {
            return existing.get().getSemesterStart();
        }
        if (semesterTerm.equals(normalizeSemesterTerm(null, fallbackDate)) && fallbackDate != null) {
            return fallbackDate;
        }
        return defaultSemesterStart(academicYear, semesterTerm);
    }

    private void upsertSemesterSetting(
            Long userId,
            String academicYear,
            Integer semesterTerm,
            String semesterCode,
            LocalDate semesterStart,
            boolean selected) {
        ScheduleSemesterSetting setting = scheduleSemesterSettingRepository
                .findByUserIdAndAcademicYearAndSemesterTerm(userId, academicYear, semesterTerm)
                .orElseGet(() -> {
                    ScheduleSemesterSetting created = new ScheduleSemesterSetting();
                    created.setUserId(userId);
                    created.setAcademicYear(academicYear);
                    created.setSemesterTerm(semesterTerm);
                    return created;
                });

        setting.setSemesterCode(semesterCode);
        setting.setSemesterStart(semesterStart);
        setting.setSelectedFlag(selected);
        scheduleSemesterSettingRepository.save(setting);
    }

    private SemesterSelection resolveSemester(Long userId, String requestedAcademicYear, Integer requestedSemesterTerm) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
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

        String academicYear = normalizeAcademicYear(requestedAcademicYear, user.getSemesterStart());
        Integer semesterTerm = normalizeSemesterTerm(requestedSemesterTerm, user.getSemesterStart());
        var setting = scheduleSemesterSettingRepository.findByUserIdAndAcademicYearAndSemesterTerm(userId, academicYear, semesterTerm);
        LocalDate semesterStart = setting
                .map(ScheduleSemesterSetting::getSemesterStart)
                .orElseGet(() -> defaultSemesterStart(academicYear, semesterTerm));
        return new SemesterSelection(academicYear, semesterTerm, semesterCodeForTerm(semesterTerm), semesterStart);
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

    private int academicYearStart(String academicYear) {
        if (academicYear != null && academicYear.matches("\\d{4}-\\d{4}")) {
            return Integer.parseInt(academicYear.substring(0, 4));
        }
        return LocalDate.now().getYear();
    }

    private String semesterCodeForTerm(Integer semesterTerm) {
        return Integer.valueOf(2).equals(semesterTerm) ? "12" : "3";
    }

    private LocalDate parseDateOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate defaultSemesterStart(String academicYear, Integer semesterTerm) {
        int startYear = academicYearStart(academicYear);
        if (Integer.valueOf(2).equals(semesterTerm)) {
            return LocalDate.of(startYear + 1, 3, 1);
        }
        return LocalDate.of(startYear, 9, 1);
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

    @lombok.Data
    public static class ScheduleImportRequest {
        private String academicYear;
        private Integer selectedSemesterTerm;
        private Boolean importBothTerms;
        private Map<String, String> semesterStarts;
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
