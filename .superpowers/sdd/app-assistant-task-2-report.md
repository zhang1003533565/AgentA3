# APP 助手 Task 2 实施报告

## 结果

AI 生成导出已改为仓库内开发持久目录、完整 UUID `storageKey`、payload 与 sidecar 双原子提交，并新增只接受逐文件 capability 的内部下载接口。FastAPI 不再公开挂载导出目录。

## TDD 证据

### RED

先扩展 `tests/test_generated_exporter.py` 并新建 `tests/test_generated_export_download.py`，再运行：

```text
cd ai-servers && .venv/bin/python -m pytest tests/test_generated_exporter.py tests/test_generated_export_download.py -q
```

首次结果：`9 failed, 3 passed`。失败原因分别命中缺失的完整 UUID/sidecar 元数据、仓库默认目录与生产门禁、capability 下载、常量时间比较，以及仍存在的公开 `StaticFiles` 挂载。

### GREEN

- 目标测试：`12 passed in 0.90s`。
- 目标测试 + SSE 回归：`16 passed in 1.17s`。
- Task 1 resource builder 回归：`45 passed in 0.07s`。
- 目标文件 `compileall`：在 `PYTHONPYCACHEPREFIX=/tmp/agenta3-task2-pycache` 下退出码 0。
- 配置冒烟：`AI_ENV=production AI_EXPORT_ROOT=/tmp/agenta3-prod-exports AI_EXPORT_TTL_HOURS=24 AI_EXPORT_MAX_BYTES=2048` 正确解析为显式目录、24 小时和 2048 字节。

额外运行 `tests/test_rag_api_routes.py` 得到 `23 passed, 10 failed`。失败集中在既有智能体模型绑定、旧 framework 断言和业务工具 mock 行为，均不经过本任务新增下载路径；本任务目标、SSE 与 builder 回归未受影响。

## 变更文件

- `.gitignore`
- `ai-servers/app/rag/document_conversion/generated_exporter.py`
- `ai-servers/app/api/routes/rag.py`
- `ai-servers/app/main.py`
- `ai-servers/tests/test_generated_exporter.py`
- `ai-servers/tests/test_generated_export_download.py`
- `.superpowers/sdd/app-assistant-task-2-report.md`

未修改 Java、APP 或 Task 1 `assistant_resource_builder.py`。

## 持久化与清理边界

- 开发默认目录为已忽略的 `ai-servers/data/ai-exports`；`AI_ENV=prod/production` 且未显式设置 `AI_EXPORT_ROOT` 时导入即失败。
- 默认 TTL 为 168 小时、默认容量为 1 GiB；分别由 `AI_EXPORT_TTL_HOURS`、`AI_EXPORT_MAX_BYTES` 覆盖。
- payload 先写同目录临时文件并 `os.replace`，sidecar 也先写临时文件，最后 rename 作为提交标记。
- sidecar 严格只含 `capabilityDigest/sha256/size/mimeType/createdAt/expiresAt`，不落原始 capability。
- 导出和读取都会清理孤儿；TTL 和容量清理按 payload/sidecar 成对删除，容量超限优先删除最早创建的 pair。
- 重启恢复不依赖进程内索引，直接从 `<storageKey>.meta.json` 验证并读取。

## 安全边界

- `storageKey` 必须是规范的小写完整 UUID 加安全扩展名；路径穿越、非规范 key 和符号链接均拒绝。
- `GET /internal/rag/exports/{storage_key}` 必须提供 `X-AI-Export-Capability`；普通或伪造 `Authorization` 不授予读取权限。
- capability 先 SHA-256，再通过 `hmac.compare_digest` 与 sidecar 摘要比较。
- 读取前验证 sidecar 字段、MIME、过期时间、payload 大小和 SHA-256；过期返回 410 并成对删除，完整性失败返回 409。
- 返回附件内部字段统一包含 `storageKey/serverGenerated/internalCapability/sha256/size/createdAt/expiresAt`，且不再返回 Python 公开静态 URL。

## 顾虑与后续边界

- capability 只是 Java 到 Python 的逐文件内部凭据；APP 用户、会话、消息和资源所有权仍必须由后续 Java manifest/代理下载链路校验。
- 原子 rename 要求 payload、sidecar 临时文件与最终文件位于同一文件系统；生产部署仍需将 `AI_EXPORT_ROOT` 指向真实持久共享卷。对象存储适配器不在本任务范围。
- 未进行多进程并发压力测试、真实共享卷故障注入或 Java 端到端下载验证。
- 全量 `test_rag_api_routes.py` 的 10 个既有失败应由对应模型配置/路由测试任务单独处理，本任务未越界修改。
