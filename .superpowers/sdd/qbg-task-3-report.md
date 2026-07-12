# Task 3 报告：Java 题库生成编排与结果审查

## 状态

完成。未实现控制器。

## TDD 证据

- RED：先扩展 `QuestionGenerationServiceImplTest`，运行 `mvn -Dtest=QuestionGenerationServiceImplTest test`；测试编译因 `GenerationCommand` 与 `generate` 尚不存在而失败，符合预期。
- GREEN：实现后运行同一测试，13 项通过，0 失败，0 错误。
- 回归：运行 `mvn -Dtest=QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`，23 项通过，0 失败，0 错误。

## 修改文件

- `AppBackend/src/main/java/com/example/appbackend/service/QuestionGenerationService.java`
- `AppBackend/src/main/java/com/example/appbackend/service/impl/QuestionGenerationServiceImpl.java`
- `AppBackend/src/main/java/com/example/appbackend/service/impl/PythonAiProxyService.java`
- `AppBackend/src/test/java/com/example/appbackend/service/impl/QuestionGenerationServiceImplTest.java`
- `.superpowers/sdd/qbg-task-3-report.md`

## 实现摘要

- 新增生成命令与 `generate` 服务接口。
- 生成前动态校验题型映射、智能体启用状态和已测试模型绑定，不回退 Leader。
- 复用材料解析器；构造自动题量/最大题量上限与难度约束。
- 通过配置解析出的 `agentName` 调用 Python RAG 代理并提取答案。
- 使用 Jackson 严格解析题库 JSON，设置来源元数据后调用 `ExamQuestionService.review(request, expectedType)`。
- 空题目保留 `missingInfo` 并追加 issue；超量追加 issue 且保留全部题目，不截断。

## 提交

- 提交：见本报告对应 Git 提交。

## 自查关注点

- Python 代理继续沿用现有 `queryRag` 的模型绑定、认证、开关与请求清理逻辑。
- 非法 JSON 不进入标准审查；合法但结构、题型或数量不合规的结果均不可导入。
- 测试环境有 Mockito 动态 agent 与 Netty macOS DNS 原生库提示，但组合测试无失败或错误。

## 审查修复记录

- 为 Jackson 字面量 `null` 增加回归测试；RED 复现 `ImportRequest` 空指针，随后将空反序列化结果与语法错误统一按非法题库 JSON 安全返回，且不进入标准审查。
- 在 `PythonAiProxyServiceTest` 通过真实 `/internal/rag/query` HTTP stub 覆盖对象响应的 `answer` 提取、JSON 字符串响应兼容、缺失或非字符串 `answer` 的固定安全错误，以及 `maxQuestions == null` 时请求体不发送该字段。
- 混合题型测试捕获传给 `review` 的完整 `ImportRequest`，确认两道题均保留，并断言 `expectedType` 为 `single_choice`。
- 覆盖 `docx`、`txt`、`text` 三种输入，确认 `sourceType`、`file`、`text` 原样传入材料解析器。
- 最终验证：`mvn -Dtest=QuestionGenerationServiceImplTest,PythonAiProxyServiceTest test`，共 29 项，0 失败、0 错误、0 跳过，构建成功。
- 环境说明：默认沙箱禁止 HTTP stub 绑定本地端口且阻止 Mockito 动态附加；最终验证沿批准路径在沙箱外执行。仍有既有 Netty macOS DNS 原生库降级与 Mockito 动态 agent 警告，不影响测试结果。
