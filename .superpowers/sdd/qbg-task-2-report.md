# QBG Task 2 实现报告

## 结果

- 状态：DONE
- 提交：`4e876451f49b9b602ebb5bc29d1146ded4c9b2ab`
- 实现范围：动态题型—智能体配置解析、结构化智能体目录快照、五种题型可用性状态。

## RED 证据

- 命令：`cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest test`
- 结果：BUILD FAILURE（测试编译失败）。
- 摘要：`OptionsResponse`、`QuestionTypeOption`、`QuestionGenerationServiceImpl` 和 `AgentDescriptor` 均不存在，符合“服务实现尚不存在”的预期失败。

## GREEN 证据

1. 指定配置解析测试
   - 命令：`cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest test`
   - 结果：BUILD SUCCESS；Tests run: 5, Failures: 0, Errors: 0, Skipped: 0。
2. 代理组合回归
   - 命令：`cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`
   - 结果：BUILD SUCCESS；Tests run: 10, Failures: 0, Errors: 0, Skipped: 0。
   - 说明：现有代理测试需要绑定本机临时 HTTP 端口，沙箱内因 `SocketException: Operation not permitted` 失败；按相同命令在沙箱外复跑通过。
3. 静态检查
   - 命令：`git diff --cached --check`
   - 结果：通过。

## 改动文件

- `AppBackend/src/main/java/com/example/appbackend/dto/QuestionGenerationDTO.java`
- `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java`
- `AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java`
- `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`
- `AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java`

## 自查

- options 始终按固定顺序返回五种题型。
- 生产实现仅固定题型枚举与 `ai.question-generation.agent.` 配置键前缀，未固定任何智能体名称。
- 缺失映射不会回退 Leader；未知、停用、无模型绑定均返回明确不可用原因。
- `PythonAiProxyService` 封装 Python 原始目录，合并既有智能体启用状态并解析既有模型绑定后返回结构化描述。
- 未实现 Task 3 的生成调用、材料编排、JSON 解析或题库审查。

## 关注点

- 本任务只校验模型绑定配置是否存在；绑定指向的模型测试状态仍沿用现有 AI 配置管理契约。
- 未运行后端全量测试；已运行 Task 2 指定测试及直接受影响的 Python 代理回归测试。
- Maven 输出包含仓库既有 Netty macOS DNS 原生库警告，与本任务改动无关。
- 本报告在产品代码提交后生成，以记录真实提交哈希，因此不包含在该提交中。

## 审查修复（2026-07-12）

### 结果

- 直接覆盖 `PythonAiProxyService.getQuestionGenerationAgentCatalog()`，不再由上层服务替身掩盖真实封装行为。
- 模型绑定改为仅从 `status=1` 的 `ai.agent-bindings.*.model` 配置构建目录；停用、空白绑定均视为无绑定。
- 保持 Task 2 既有架构，仅补充真实边界测试与最小仓储过滤修复。

### RED 证据

- 命令：`cd AppBackend && mvn -Dtest=PythonAiProxyServiceTest#questionGenerationCatalog_shouldParseAgentsAndActiveModelBindings+questionGenerationCatalog_shouldMergeDisabledAgentToggle+questionGenerationCatalog_shouldIgnoreInactiveModelBinding+questionGenerationCatalog_shouldTreatBlankModelBindingAsMissing+questionGenerationCatalog_shouldReturnEmptyCatalogForMalformedOrMissingAgents test`
- 结果：Tests run: 5, Failures: 3, Errors: 0。
- 失败点：有效绑定错误读取通用默认值；`status=0` 与空白绑定仍被错误解析为已有绑定。

### GREEN 与回归证据

- 新增目录封装测试：Tests run: 5, Failures: 0, Errors: 0, Skipped: 0。
- 指定回归命令：`cd AppBackend && mvn -Dtest=QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`
- 结果：BUILD SUCCESS；Tests run: 15, Failures: 0, Errors: 0, Skipped: 0。
- 覆盖：有效 Python `agents` 结构、`ai.agent-enabled.*` 停用合并、模型绑定 `status=0`、空白绑定、畸形 `agents` 与缺失 `agents`。

### 关注点

- 这些测试使用本机临时 HTTP 端口模拟 Python `/internal/rag/agents`；受限沙箱内无法绑定端口，已在获准的沙箱外测试环境完成 RED/GREEN 与回归验证。
- Maven 仍输出仓库既有 Netty macOS DNS 原生库警告，不影响测试结果。
