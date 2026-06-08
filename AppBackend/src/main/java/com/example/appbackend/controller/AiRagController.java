package com.example.appbackend.controller;

import com.example.appbackend.entity.Result;
import com.example.appbackend.exception.BusinessException;
import com.example.appbackend.service.impl.PythonAiProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/rag")
@Tag(name = "AI RAG 管理", description = "管理员代理 Python RAG 服务的策略、知识库、智能体、框架和评估接口")
public class AiRagController {
    private static final String ROLE_ADMIN = "ADMIN";

    private final PythonAiProxyService pythonAiProxyService;

    public AiRagController(PythonAiProxyService pythonAiProxyService) {
        this.pythonAiProxyService = pythonAiProxyService;
    }

    @GetMapping("/strategies")
    @Operation(summary = "RAG 策略列表")
    public Result<Object> strategies(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagStrategies(adminAuthorization(request)));
    }

    @GetMapping("/strategies/{strategyName}")
    @Operation(summary = "RAG 策略详情")
    public Result<Object> strategy(@PathVariable String strategyName, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagStrategy(strategyName, adminAuthorization(request)));
    }

    @GetMapping("/capabilities")
    @Operation(summary = "RAG 能力目录")
    public Result<Object> capabilities(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagCapabilities(adminAuthorization(request)));
    }

    @GetMapping("/framework")
    @Operation(summary = "RAG 框架目录")
    public Result<Object> framework(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagFramework(adminAuthorization(request)));
    }

    @GetMapping("/agents")
    @Operation(summary = "RAG 多智能体列表")
    public Result<Object> agents(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagAgents(adminAuthorization(request)));
    }

    @GetMapping("/model-providers")
    @Operation(summary = "AI 模型服务商目录")
    public Result<Object> modelProviders(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getModelProviders(adminAuthorization(request)));
    }

    @GetMapping("/agents/{agentName}")
    @Operation(summary = "RAG 多智能体详情")
    public Result<Object> agent(@PathVariable String agentName, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagAgent(agentName, adminAuthorization(request)));
    }

    @PostMapping("/agents/{agentName}/example-input")
    @Operation(summary = "保存智能体示例输入")
    public Result<Object> saveAgentExampleInput(
            @PathVariable String agentName,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.updateRagAgentExampleInput(agentName, body, adminAuthorization(request)));
    }

    @PostMapping("/query")
    @Operation(summary = "执行 RAG 查询")
    public Result<Object> query(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.queryRag(body, adminAuthorization(request)));
    }

    @PostMapping("/recall-test")
    @Operation(summary = "执行 RAG 召回测试")
    public Result<Object> recallTest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.recallTestRag(body, adminAuthorization(request)));
    }

    @PostMapping("/documents")
    @Operation(summary = "写入 RAG 知识库")
    public Result<Object> ingestDocuments(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.ingestRagDocuments(body, adminAuthorization(request)));
    }

    @PostMapping(value = "/pdf/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "PDF 转换为 Markdown 或 DOCX")
    public Result<Object> convertPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetFormat") String targetFormat,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.convertPdf(file, targetFormat, adminAuthorization(request)));
    }

    @PostMapping(value = "/ppt/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "PPTX 转换为 DOCX")
    public Result<Object> convertPpt(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.convertPpt(file, adminAuthorization(request)));
    }

    @GetMapping("/documents")
    @Operation(summary = "RAG 知识库文档列表")
    public Result<Object> documents(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.listRagDocuments(adminAuthorization(request)));
    }

    @GetMapping("/documents/chunks")
    @Operation(summary = "RAG 知识库文档切片列表")
    public Result<Object> documentChunks(
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "knowledgeBaseIds", required = false) String knowledgeBaseIds,
            HttpServletRequest request) {
        return Result.success(pythonAiProxyService.listRagDocumentChunks(source, knowledgeBaseIds, adminAuthorization(request)));
    }

    @GetMapping("/vector-store/health")
    @Operation(summary = "向量库健康检查")
    public Result<Object> vectorStoreHealth(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagVectorStoreHealth(adminAuthorization(request)));
    }

    @GetMapping("/embedding/health")
    @Operation(summary = "Embedding 服务健康检查")
    public Result<Object> embeddingHealth(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagEmbeddingHealth(adminAuthorization(request)));
    }

    @GetMapping("/graph-store/health")
    @Operation(summary = "图谱存储健康检查")
    public Result<Object> graphStoreHealth(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getRagGraphStoreHealth(adminAuthorization(request)));
    }

    @GetMapping("/text-to-sql/schema")
    @Operation(summary = "Text-to-SQL Schema")
    public Result<Object> textToSqlSchema(HttpServletRequest request) {
        return Result.success(pythonAiProxyService.getTextToSqlSchema(adminAuthorization(request)));
    }

    @PostMapping("/text-to-sql/execute")
    @Operation(summary = "执行 Text-to-SQL")
    public Result<Object> executeTextToSql(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.executeTextToSql(body, adminAuthorization(request)));
    }

    @PostMapping("/evaluate")
    @Operation(summary = "RAG 评估")
    public Result<Object> evaluate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return Result.success(pythonAiProxyService.evaluateRag(body, adminAuthorization(request)));
    }

    private String adminAuthorization(HttpServletRequest request) {
        if (!ROLE_ADMIN.equals(request.getAttribute("role"))) {
            throw new BusinessException(Result.FORBIDDEN_CODE, "无权限操作，仅管理员可执行");
        }
        return request.getHeader("Authorization");
    }
}
