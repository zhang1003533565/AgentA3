package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.entity.User;
import com.example.appbackend.repository.UserRepository;
import com.example.appbackend.service.CourseScheduleService;
import com.example.appbackend.service.PlaywrightService;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${browser.headless:true}")
    private boolean headless;

    @Value("${browser.default-url:https://jwx.hebiace.edu.cn/}")
    private String defaultUrl;

    public PlaywrightController(PlaywrightService playwrightService,
                                CourseScheduleService courseScheduleService,
                                UserRepository userRepository) {
        this.playwrightService = playwrightService;
        this.courseScheduleService = courseScheduleService;
        this.userRepository = userRepository;
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
            List<Map<String, Object>> courseBlocks = (List<Map<String, Object>>) playwrightService.evaluate(newPage,
                "() => { " +
                "var blocks = []; " +
                "var timetableConElements = document.querySelectorAll('div.timetable_con'); " +
                "timetableConElements.forEach(function(el, idx) { " +
                "  blocks.push({ " +
                "    index: idx + 1, " +
                "    outerHTML: el.outerHTML, " +
                "    innerText: el.innerText " +
                "  }); " +
                "}); " +
                "return blocks; " +
                "}"
            );

            // 构建 rawData 格式用于解析保存
            StringBuilder rawDataBuilder = new StringBuilder();
            for (Map<String, Object> block : courseBlocks) {
                rawDataBuilder.append("=== 课表块 ").append(block.get("index")).append(" ===\n");
                rawDataBuilder.append("\"innerText\": \"").append(block.get("innerText")).append("\"\n");
            }
            String rawData = rawDataBuilder.toString();

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

            // 获取所有课表块
            String rawData = (String) playwrightService.evaluate(newPage,
                "() => { " +
                "let output = []; " +
                "var timetableConElements = document.querySelectorAll('div.timetable_con'); " +
                "timetableConElements.forEach(function(el, idx) { " +
                "output.push('=== 课表块 ' + (idx + 1) + ' ==='); " +
                "output.push(el.innerText); " +
                "output.push(''); " +
                "}); " +
                "return output.join('\\n'); " +
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