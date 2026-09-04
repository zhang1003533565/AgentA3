package com.example.appbackend.service;

import com.example.appbackend.dto.CodeCanvasDTO;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.repository.SystemConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码画布服务：接收用户输入的后端程序代码，调用文本大模型生成前端预览页面（单文件 HTML）。
 *
 * <p>AI 配置读取顺序与 FlowchartAIServiceImpl 保持一致：
 * code_canvas_agent 绑定 → leader_agent 绑定 → 已测试通过的 text 配置 → 任意完整 text 配置 → ai.service.text 兜底。
 * 本类为纯新增，不修改任何已有 Service / Controller。
 */
@Service
public class CodeCanvasService {

    private static final int MAX_INPUT_CHARS = 60_000;
    private static final String CODE_CANVAS_AGENT_NAME = "code_canvas_agent";
    private static final String DEFAULT_AGENT_NAME = "leader_agent";
    private static final String AGENT_MODEL_BINDING_PREFIX = "ai.agent-bindings.";
    private static final Duration AI_TIMEOUT = Duration.ofSeconds(180);

    private final SystemConfigService systemConfigService;
    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    public CodeCanvasService(SystemConfigService systemConfigService,
                             SystemConfigRepository systemConfigRepository,
                             ObjectMapper objectMapper) {
        this.systemConfigService = systemConfigService;
        this.systemConfigRepository = systemConfigRepository;
        this.objectMapper = objectMapper;
    }

    public CodeCanvasDTO.GenerateResponse generate(CodeCanvasDTO.GenerateRequest request) {
        if (request == null || !StringUtils.hasText(request.getCode())) {
            throw new BusinessException(400, "请输入后端程序代码");
        }

        String configPrefix = resolveTextConfigPrefix();
        if (!StringUtils.hasText(configPrefix)) {
            throw new BusinessException(400,
                    "AI 文本模型未配置，请在系统配置中维护 ai.service.text.* 或 ai.agent-bindings." + DEFAULT_AGENT_NAME + ".model");
        }
        String apiKey = requireAiConfig(configPrefix, "api-key", "AI Key");
        String baseUrl = trimTrailingSlash(requireAiConfig(configPrefix, "base-url", "AI 服务地址"));
        String model = requireAiConfig(configPrefix, "model", "AI 模型 ID");

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", """
                你是一名资深全栈工程师，擅长根据后端程序代码快速设计并实现前端管理页面。
                你必须只返回严格 JSON，不要输出 Markdown、解释、代码块标记或任何多余文本。
                """);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", buildPrompt(request));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(systemMessage, userMessage));
        payload.put("temperature", 0.4);

        try {
            String responseText = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(AI_TIMEOUT)
                    .block();

            JsonNode root = objectMapper.readTree(responseText);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(500, "AI 未返回内容");
            }
            return parseResponse(content);
        } catch (WebClientResponseException error) {
            throw new BusinessException(500, "AI 请求失败: " + error.getResponseBodyAsString());
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(500, "代码画布生成失败: " + error.getMessage());
        }
    }

    private String buildPrompt(CodeCanvasDTO.GenerateRequest request) {
        String code = request.getCode().trim();
        if (code.length() > MAX_INPUT_CHARS) {
            code = code.substring(0, MAX_INPUT_CHARS);
        }
        String requirement = StringUtils.hasText(request.getRequirement())
                ? request.getRequirement().trim() : "（无额外要求，请根据代码自行推断最合适的页面形态）";
        String titleHint = StringUtils.hasText(request.getTitle())
                ? "用户指定页面标题：" + request.getTitle().trim() : "（标题请根据代码推断）";

        return """
                请根据下面的【后端程序代码】，分析其业务实体、字段、接口与数据关系，生成一个可直接运行的前端预览页面。

                【后端程序代码】
                ```
                %s
                ```

                【用户补充要求】
                %s
                %s

                【生成要求｜必须全部遵守】
                1. 输出严格 JSON，结构如下（不要输出除 JSON 外的任何字符）：
                {
                  "title": "页面标题，如：公共设施管理",
                  "summary": "用 2-4 句话说明这个后端模块的业务用途，以及你生成的页面包含哪些功能区域",
                  "entities": "识别到的实体名、关键字段及其中文含义，用简洁的中文条目列出",
                  "html": "完整的单文件 HTML 文档内容"
                }
                2. html 字段必须是一个【完整的 HTML 文档】：以 <!DOCTYPE html> 开头，以 </html> 结尾。
                3. html 必须自包含、零外部依赖：
                   - 不允许引用任何外部 CDN、外部 CSS、外部 JS、外部图片或字体；
                   - 所有样式用 <style> 内联，所有脚本用 <script> 内联；
                   - 图标统一用 emoji 或纯 CSS 绘制，不要用图标字体。
                4. 页面风格模仿 Ant Design Pro 后台管理：
                   - 顶部页头（模块名 + 简短说明）、搜索/筛选栏、主操作按钮（如"新增"）、数据表格、分页栏；
                   - 主色 #1677ff，圆角 6-8px，卡片阴影柔和，间距宽松，整体简洁现代；
                   - 页面内文字全部使用中文。
                5. 表格列必须来自代码中真实存在的实体字段，字段标题翻译成易懂的中文；根据字段语义合理使用：
                   - 状态类字段用彩色标签（如 启用/禁用、正常/异常）；
                   - 图片/头像 URL 字段渲染为缩略图（无真实图片时用占位色块或 emoji）；
                   - 时间字段格式化为 yyyy-MM-dd HH:mm；
                   - 操作列放"编辑 / 删除"文字按钮。
                6. html 内置 6-10 条符合业务语义的中文 mock 数据（写在 <script> 中渲染），让页面开箱即用。
                7. 交互用原生 JavaScript 实现即可，但以下功能必须可用：
                   - 关键字搜索框能过滤表格行；
                   - "新增"按钮弹出表单弹窗（字段与实体对应），提交后把新行追加到表格；
                   - "删除"按钮能移除对应行（带 confirm 确认）；
                   - 分页栏可切换页码（每页 5 或 10 条）。
                8. 严禁在 html 中出现 alert 报错、未定义变量或死链；确保双击打开即可正常使用。
                9. JSON 中的 html 字段必须正确转义：双引号转义为 \\"，换行转义为 \\n，保证整体是合法 JSON。
                """.formatted(code, requirement, titleHint);
    }

    private CodeCanvasDTO.GenerateResponse parseResponse(String content) throws Exception {
        String json = extractJson(content);
        JsonNode root = objectMapper.readTree(json);

        CodeCanvasDTO.GenerateResponse response = new CodeCanvasDTO.GenerateResponse();
        response.setTitle(root.path("title").asText("代码画布预览"));
        response.setSummary(root.path("summary").asText(""));
        response.setEntities(root.path("entities").asText(""));
        String html = root.path("html").asText("");
        if (!StringUtils.hasText(html) || !html.contains("<")) {
            throw new BusinessException(500, "AI 未返回有效的 HTML 页面内容，请重试");
        }
        response.setHtml(html);
        return response;
    }

    private String extractJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?is)^```(?:json)?\\s*", "").replaceFirst("(?is)\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(500, "AI 未返回 JSON 对象，请重试");
        }
        return text.substring(start, end + 1);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String requireAiConfig(String configPrefix, String field, String label) {
        String key = configPrefix + "." + field;
        String value = systemConfigService.getValue(key, "");
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, label + " 未配置，请在系统配置中维护 " + key);
        }
        return value;
    }

    /**
     * 解析当前可用的文本模型配置前缀。
     * 顺序：代码画布专属绑定 → leader_agent 绑定 → 已测试通过的 text 配置 → 任意完整 text 配置 → 兜底 ai.service.text。
     */
    private String resolveTextConfigPrefix() {
        String bound = firstText(
                resolveAgentBoundModel(CODE_CANVAS_AGENT_NAME),
                resolveAgentBoundModel(DEFAULT_AGENT_NAME),
                firstTestedTextConfigPrefix(),
                firstCompleteTextConfigPrefix()
        );
        if (StringUtils.hasText(bound)) {
            return bound;
        }
        return hasCompleteConfig("ai.service.text") ? "ai.service.text" : "";
    }

    private String resolveAgentBoundModel(String agentName) {
        String key = AGENT_MODEL_BINDING_PREFIX + agentName + ".model";
        String value = systemConfigService.getValue(key, "");
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private boolean hasCompleteConfig(String configPrefix) {
        return StringUtils.hasText(systemConfigService.getValue(configPrefix + ".api-key", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".base-url", ""))
                && StringUtils.hasText(systemConfigService.getValue(configPrefix + ".model", ""));
    }

    private String firstTestedTextConfigPrefix() {
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".tested-fingerprint"))
                .filter(config -> StringUtils.hasText(config.getConfigValue()))
                .map(config -> removeSuffix(config.getConfigKey(), ".tested-fingerprint"))
                .filter(this::hasCompleteConfig)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String firstCompleteTextConfigPrefix() {
        return systemConfigRepository.findByConfigKeyStartingWithAndStatus("ai.service.text.", 1)
                .stream()
                .filter(config -> config.getConfigKey() != null && config.getConfigKey().endsWith(".model"))
                .map(config -> removeSuffix(config.getConfigKey(), ".model"))
                .filter(this::hasCompleteConfig)
                .sorted()
                .findFirst()
                .orElse("");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String removeSuffix(String value, String suffix) {
        if (value == null) {
            return "";
        }
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }
}
