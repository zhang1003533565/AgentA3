package com.example.appbackend.controller;

import com.example.appbackend.dto.ActivityAgentGenerateRequest;
import com.example.appbackend.dto.ActivityAgentGenerateResponse;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.ActivityAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity/ai")
@Tag(name = "活动 AI 辅助发布", description = "调用 activity_publish_agent 生成活动草稿，仅返回草稿，不创建活动、不落库")
public class ActivityAgentController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_TEACHER = "TEACHER";

    private final ActivityAgentService activityAgentService;

    public ActivityAgentController(ActivityAgentService activityAgentService) {
        this.activityAgentService = activityAgentService;
    }

    @PostMapping("/generate")
    @Operation(summary = "AI 生成活动草稿",
            description = "根据管理员自然语言和当前草稿生成/补全活动表单数据；返回 activity/missingFields 等供前端回填，不创建活动")
    public Result<ActivityAgentGenerateResponse> generate(
            @Valid @RequestBody ActivityAgentGenerateRequest request,
            HttpServletRequest httpRequest) {
        checkRole(httpRequest);
        String authorization = httpRequest.getHeader("Authorization");
        return Result.success(activityAgentService.generate(request, authorization));
    }

    private void checkRole(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        if (role == null || (!ROLE_ADMIN.equals(role) && !ROLE_TEACHER.equals(role))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员、教师可执行");
        }
    }
}
