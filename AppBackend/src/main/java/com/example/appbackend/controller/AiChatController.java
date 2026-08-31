package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.service.AgentModelBindService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * AI Chat Controller - 智能体对话转发
 * 接收前端消息，根据 agentId 获取模型配置，转发到 Python LLM 服务
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AgentModelBindService agentModelBindService;

    /**
     * 非流式对话接口
     */
    @PostMapping("/agent/{agentId}")
    public Result<Map<String, Object>> chat(
            @PathVariable String agentId,
            @RequestBody Map<String, Object> request) {
        
        log.info("收到 AI 对话请求：agentId={}, message={}", agentId, request.get("message"));
        
        try {
            // 1. 获取模型配置
            Map<String, Object> bindInfo = agentModelBindService.getBindInfoByAgent(agentId);
            
            // 2. 转发到 Python 服务
            @SuppressWarnings("unchecked")
            Map<String, Object> modelConfig = (Map<String, Object>) bindInfo.get("modelConfig");
            String baseUrl = (String) modelConfig.get("baseUrl");
            String apiKey = (String) modelConfig.get("apiKey");
            String modelName = (String) modelConfig.get("modelName");
            
            // 组装完整请求体
            Map<String, Object> payload = assemblePythonRequest(request, modelName);
            
            // 3. 调用 Python 服务并等待响应
            String response = callPythonLLM(baseUrl, apiKey, payload);
            
            Map<String, Object> result = Map.of(
                    "success", true,
                    "message", "AI 回复生成成功",
                    "content", extractContentFromPythonResponse(response),
                    "source", "python-llm"
            );
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("AI 对话失败：agentId=" + agentId, e);
            return Result.error("AI 对话失败：" + e.getMessage());
        }
    }

    /**
     * 流式对话接口（SSE）
     */
    @PostMapping(value = "/agent/{agentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable String agentId, @RequestBody Map<String, Object> request) {
        
        SseEmitter emitter = new SseEmitter(0L); // 无限超时
        
        // 异步执行
        new Thread(() -> {
            try {
                // 1. 获取模型配置
                Map<String, Object> bindInfo = agentModelBindService.getBindInfoByAgent(agentId);
                
                // 2. 构造 SSE 输出
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data(Map.of("status", "starting")));
                
                // 3. 调用 Python 服务
                @SuppressWarnings("unchecked")
                Map<String, Object> modelConfig = (Map<String, Object>) bindInfo.get("modelConfig");
                String baseUrl = (String) modelConfig.get("baseUrl");
                String apiKey = (String) modelConfig.get("apiKey");
                
                Map<String, Object> payload = assemblePythonRequest(request, 
                        (String) modelConfig.get("modelName"));
                
                callPythonLLMStream(emitter, baseUrl, apiKey, payload);
                
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                    emitter.completeWithError(e);
                } catch (IOException ioException) {
                    log.error("SSE 错误", ioException);
                }
            }
        }).start();
        
        return emitter;
    }

    /**
     * 组装发送给 Python 的请求体
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> assemblePythonRequest(Map<String, Object> userRequest, String modelName) {
        // 注意：具体的提示词组装逻辑应该在 Java 后端完成
        // Python 只负责调用 DeepSeek 大模型
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("agent_id", userRequest.get("agentId"));
        payload.put("user_message", userRequest.get("message"));
        payload.put("system_prompt", buildSystemPrompt(userRequest.get("agentId")));
        payload.put("model_name", modelName);
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 2000);
        
        // 可选参数：上下文对话
        if (userRequest.containsKey("context")) {
            payload.put("context", userRequest.get("context"));
        }
        
        return payload;
    }

    /**
     * 构建 System Prompt（根据智能体类型）
     */
    private String buildSystemPrompt(Object agentId) {
        if ("resume-editor".equals(agentId)) {
            return "你是一位专业的简历修改助手，帮助用户优化简历内容，使其更符合招聘要求。";
        } else if ("resume-generator".equals(agentId)) {
            return "你是一位简历生成专家，根据用户提供的个人信息和工作经历，生成专业格式的简历内容。";
        } else {
            return "你是一位有帮助的助手。";
        }
    }

    /**
     * 调用 Python LLM 服务（非流式）
     */
    @SuppressWarnings("unchecked")
    private String callPythonLLM(String baseUrl, String apiKey, Map<String, Object> payload) throws IOException {
        try {
            HttpClient client = HttpClient.newHttpClient();
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonBody = mapper.writeValueAsString(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new RuntimeException("Python 服务调用失败：" + response.statusCode() + " - " + response.body());
            }
            
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("调用被中断", e);
        }
    }

    /**
     * 调用 Python LLM 服务（SSE 流式）
     */
    @SuppressWarnings("unchecked")
    private void callPythonLLMStream(SseEmitter emitter, String baseUrl, String apiKey, Map<String, Object> payload) {
        new Thread(() -> {
            try {
                emitter.send(SseEmitter.event().name("start").data(Map.of("status", "starting")));
                
                HttpClient client = HttpClient.newHttpClient();
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String jsonBody = mapper.writeValueAsString(payload);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(java.net.URI.create(baseUrl + "/api/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                
                // 对于流式响应，需要特殊处理 SSE 格式
                HttpResponse<byte[]> response = client.send(request,
                        HttpResponse.BodyHandlers.ofByteArray());
                
                if (response.statusCode() != 200) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", "Python 服务调用失败：" + response.statusCode())));
                    emitter.completeWithError(new RuntimeException("HTTP " + response.statusCode()));
                    return;
                }
                
                String content = new String(response.body(), "UTF-8");
                emitter.send(SseEmitter.event().name("token").data(Map.of("content", content)));
                emitter.send(SseEmitter.event().name("end").data(Map.of("status", "completed")));
                
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                    emitter.completeWithError(e);
                } catch (IOException ioException) {
                    log.error("SSE 流式调用错误", ioException);
                }
            }
        }).start();
    }

    /**
     * 从 Python 响应中提取内容
     */
    private String extractContentFromPythonResponse(String json) {
        // 简单解析：查找 content 字段
        int start = json.indexOf("\"content\"");
        if (start == -1) return json;
        
        int colon = json.indexOf(':', start);
        if (colon == -1) return json;
        
        int quoteStart = json.indexOf('"', colon);
        if (quoteStart == -1) return json;
        
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return json;
        
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result<String> healthCheck() {
        return Result.success("AI 对话服务正常");
    }
}
