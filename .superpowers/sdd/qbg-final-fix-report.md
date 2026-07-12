# QBG 最终整合修复报告

## 状态

- 完成 Critical/Important：生成导入信任边界、服务端模型测试指纹、DOCX ZIP/文本资源边界。
- 完成 Minor：返回并展示实际模型、材料字符数和安全摘要；DOCX 能力提示；生成服务结构化安全日志。

## RED 证据

1. DOCX 资源测试先加入五参数限制构造器和超限断言；`mvn -Dtest=QuestionGenerationMaterialParserTest test` 在 testCompile 阶段因构造器不存在失败。
2. 前端导入契约改为仅发送 proof 后，旧测试明确失败：实际 payload 不再包含 `sourceAgent/sourceTitle/sourceScene`，与旧期望不一致。
3. 新模型指纹测试覆盖已测试配置的 model 修改后 options 立即不可用；proof 测试覆盖服务端来源写入和同一 proof 二次导入失败。

## 实现

- `generate` 仅向管理员开放并返回 15 分钟一次性随机 proof；服务端保存题型、实际智能体、来源标题和过期时间。
- 新增管理员专用 `/api/exam/question-generation/import`。它原子移除 proof，使用编辑后的题目调用现有 `ExamQuestionService.importQuestions`（内部重新审查），来源元数据仅由服务端 proof 写入。
- 普通 `/api/exam/questions/import` 拒绝 `sourceScene=question_generation`，manual/import 既有契约保持不变。
- AI 模型测试成功后在 `system_config` 写入绑定 provider/base-url/api-key/model 的 SHA-256 指纹；题库生成重算并比对。缺项、未测试或配置修改均不可用。
- DOCX 在 POI 打开前流式预检 ZIP：单 entry 16 MiB、累计 50 MiB、2000 entries；保持 POI 全局安全参数不变。文本提取使用增量 StringBuilder，超过字符上限立即中止。
- 结构化日志只记录 type/agent/model/chars/max/actual/duration/status，不记录材料、令牌、密钥或完整答案。

## GREEN 与验证

- 后端完整相关组合（含 `PythonAiProxyServiceTest`、`ExamQuestionControllerTest`）：61/61，0 failure/error；需要本机临时 HTTP 端口，已在沙箱外获准执行。
- 前端全部 Node：41/41。
- scoped ESLint：退出码 0。
- Vite production build：退出码 0，3743 modules transformed；仅有既有 chunk-size warning。
- `git diff --check`：通过。

## 关注点

- proof 是当前单实例服务进程内短期状态；若未来水平扩容，应迁移到 Redis/数据库或改用带服务端 nonce 消费记录的签名 token。
- 模型指纹包含 API key 的摘要但不存明文副本；配置修改会失效，需重新执行模型测试。
- 未执行真实外部 AI 凭据 E2E。
