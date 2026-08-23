package com.example.appbackend.controller;

import com.example.appbackend.dto.PythonProblemDTO;
import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.PythonProblemService;
import com.example.appbackend.service.impl.PythonAiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Python 在线编程题库接口
 * - /list、/detail 为公开接口（JwtInterceptor 白名单）
 * - 其余为管理端接口，需 ADMIN 角色
 */
@RestController
@RequestMapping("/api/python-problem")
@Tag(name = "Python在线编程题库", description = "题库列表、详情与管理接口")
public class PythonProblemController {

    @Autowired
    private PythonProblemService pythonProblemService;

    @Autowired
    private PythonAiProxyService pythonAiProxyService;

    private static final String ROLE_ADMIN = "ADMIN";

    private boolean isAdmin(HttpServletRequest request) {
        String role = (String) request.getAttribute("role");
        return ROLE_ADMIN.equals(role);
    }

    // ==================== 小程序端 ====================

    @GetMapping("/list")
    @Operation(summary = "题库列表", description = "公开接口，仅返回上架题目摘要")
    public Result<List<PythonProblemDTO.SummaryVO>> list() {
        return Result.success(pythonProblemService.listPublic());
    }

    @GetMapping("/detail")
    @Operation(summary = "题目详情", description = "公开接口，含测试用例（编程页判题使用）")
    public Result<PythonProblemDTO.DetailVO> detail(@RequestParam Long id) {
        return Result.success(pythonProblemService.getDetail(id));
    }

    /**
     * AI 辅助编程（LeetCode 式：提示/思路/代码解释/报错分析）。
     * 登录用户可用；请求体由 ai-servers 的 python_coding_tutor_agent 消费，SSE 流式返回。
     */
    @PostMapping("/ai-assist/stream")
    @Operation(summary = "AI 辅助编程（流式）", description = "登录用户可用：分级提示/思路讲解/代码解释/报错分析")
    public SseEmitter aiAssistStream(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            // 注入标准答案（多解）供 AI 参照：从库中读取并塞入转发的 problem.solution，
            // 不经过小程序公开接口，避免标准答案泄露给前端。
            Object problem = body.get("problem");
            if (problem instanceof Map<?, ?> problemMap) {
                Object rawId = ((Map<?, ?>) problemMap).get("id");
                if (rawId != null) {
                    try {
                        Long problemId = Long.valueOf(String.valueOf(rawId));
                        String solution = pythonProblemService.getSolutionJson(problemId);
                        if (solution != null) {
                            ((Map<String, Object>) problemMap).put("solution", solution);
                        }
                    } catch (NumberFormatException ignored) {
                        // 题目 id 非法时跳过注入，AI 仍可基于题面回答
                    }
                }
            }
            return pythonAiProxyService.streamCodingAssist(body, authorization);
        } catch (BusinessException e) {
            // 模型未配置等前置校验失败时，以 SSE error 事件返回友好信息（而非 HTTP 500）
            SseEmitter emitter = new SseEmitter(0L);
            try {
                Map<String, Object> failure = new java.util.HashMap<>();
                failure.put("message", e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(failure, MediaType.APPLICATION_JSON));
            } catch (Exception ignored) {
                // 客户端已断开时忽略
            }
            emitter.complete();
            return emitter;
        }
    }

    // ==================== 管理端 ====================

    @GetMapping("/admin/list")
    @Operation(summary = "题目管理列表", description = "管理员权限，支持关键字/难度/上下架筛选")
    public Result<List<PythonProblemDTO.AdminVO>> adminList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Boolean enabled,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(pythonProblemService.listAdmin(keyword, difficulty, enabled));
    }

    @PostMapping("/ai-generate")
    @Operation(summary = "AI 生成题目", description = "管理员权限：对话式需求生成题目草案（未入库），返回 AI 理解到的规格与题目预览列表")
    public Result<PythonProblemDTO.AIGenerateResponse> aiGenerate(
            @RequestBody PythonProblemDTO.AIGenerateRequest req,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success(pythonProblemService.aiGenerate(req, authorization));
    }

    @PostMapping
    @Operation(summary = "新增题目", description = "管理员权限")
    public Result<PythonProblemDTO.AdminVO> create(
            @RequestBody PythonProblemDTO.ProblemRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success("创建成功", pythonProblemService.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑题目", description = "管理员权限")
    public Result<PythonProblemDTO.AdminVO> update(
            @PathVariable Long id,
            @RequestBody PythonProblemDTO.ProblemRequest req,
            HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        return Result.success("更新成功", pythonProblemService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除题目", description = "管理员权限")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) throw new BusinessException(Result.FORBIDDEN_CODE, "无权限");
        pythonProblemService.delete(id);
        return Result.success("删除成功", (Void) null);
    }
}
