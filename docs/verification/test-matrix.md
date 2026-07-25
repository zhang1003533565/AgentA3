# A3 提交验证矩阵

## 最新本地门禁

- 执行时间：2026-07-15 19:28（Asia/Shanghai）
- 代码基线：`01759a1` 及其祖先；本文件只增加验证记录。
- 命令：`bash scripts/ci/quality-gate.sh`
- 结果：退出码 0，六个阶段全部通过。
- 环境：Java 21.0.11、Maven 3.9.16、Python 3.9.6 本地 venv、Node 22.22.2、npm 10.9.7、Docker 29.4.0、Compose v5.1.1。
- 网络边界：测试只使用 `127.0.0.1` 临时服务；未访问外网、MaxKB 或模型供应商。

CI 使用 Java 21、Python 3.11 和 Node 20；本地通过不能替代 GitHub Actions 的干净环境结果。

## 自动化结果

| 层 | 命令/门禁 | 最新结果 | 覆盖重点 | 尚未覆盖 |
| --- | --- | --- | --- | --- |
| AppBackend | `mvn -q test` | 389 tests，0 failures，0 errors，1 skipped | 鉴权、MaxKB 门面、学习路径、考试反馈、资源证据、题库、文档预览、Java→Python 代理与内部令牌 | opt-in 视觉矩阵未在本次运行生成；真实 MySQL/MaxKB 未接入 |
| ai-servers | `.venv/bin/python -m pytest -q` | 264 passed，5 warnings | Leader 路由、多智能体、资源审核/导出、SSE、Java 工具复用、日期课表、内部令牌、Redis/Java 地址 | 真实模型、多模态供应商与 MaxKB 未调用 |
| AppWeb | Node tests | 46/46 passed | 题库、试卷预览、菜单、React 19 兼容、地图实例生命周期 | 浏览器端真实登录和视觉回归 |
| AppWeb | `npm run lint` | 0 errors，0 warnings | ESLint 与 Hooks 规则 | 无 |
| AppWeb | `npm run build` | PASS | Vite production build | 主 JS chunk 约 2.8 MB，存在 >500 kB 警告 |
| AppFrontend | 全部 `*.test.js` | 93/93 passed | 校园工具保留、学习中心、画像补问、六状态、资源卡、考试闭环、安全 Markdown、SSE 恢复 | 真机、微信开发者工具、弱网与视觉检查 |
| Release contracts | Python unittest | 9/9 passed | 知识清单 2、事实评测 3、压测计划 4 | 不等价于在线评测 |
| Knowledge | `validate_python_course.py` | PASS | 清单字段、许可证、路径、哈希、评测引用 | manifest 仍为 `needs_export`，没有真实来源包 |
| Factual set | `run_factual_eval.py --validate-only` | PASS | 20 deterministic + 5 rubric + 5 refusal | 在线报告为 `not_run`，指标全部 null |
| Load plan | `run_load.py --validate-only` | PASS | 最少 5 并发、50 请求和固定阈值 | 在线报告为 `not_run`，没有真实性能指标 |
| Deployment | `docker compose ... config --quiet` | PASS | 五服务、健康依赖、内部 DNS、令牌/地址注入 | 未构建镜像、未 `up -d`、未执行 `deploy/verify.sh` |

Java 唯一 skipped 为 `SourcePaperVisualFixtureTest.writesSourceFaithfulVisualMatrix`，该测试只在提供 `-Dexam.visual.output=...` 时生成视觉 QA 文件，不是功能失败。

## 赛题需求到证据

| 赛题项 | 自动化/代码证据 | 当前结论 | 演示前必做 |
| --- | --- | --- | --- |
| 对话式动态画像 ≥6 维 | AppFrontend `learningProfileDialog.test.js`；Java 画像证据由 Leader/考试反馈链路覆盖 | 结构和交互有测试 | 用匿名学生完成一次对话，截图七维画像与版本变化 |
| 多智能体 ≥5 类资源 | `ai-servers/tests/test_learning_workflow.py`、`test_learning_exports.py` | DAG、审核、导出有离线测试 | 用真实 MaxKB/模型生成至少 5 类成功资源并保存原始事件/文件 |
| 个性化路径与推送 | `LearningPathServiceImplTest`、`LearningPathPersistenceTest`、App learning tests | 后端事实与前端状态有测试 | 演示答题前后 path version、弱点和推荐变化 |
| 智能辅导（加分） | Leader、资源信封、Java 工具复用、双助手页面测试 | 离线契约通过 | 真实多轮问答、图解/文件与资料不足拒答 |
| 学习效果评估（加分） | `AppExamLearningFeedbackTest`/PersistenceTest、exam frontend tests | 考试→掌握度/路径反馈有测试 | 使用同一学生账号展示一次真实提交与重规划 |
| 防幻觉/内容安全 | 证据完整性、模型-only 不可信、严格题库 schema、安全 Markdown、拒答金标 | 确定性门禁存在 | 冻结真实来源并运行 30 题；人工复核 5 道解释题 |
| 响应体验 | SSE、单项失败/重试、六状态、工作流恢复测试 | 交互契约通过 | 弱网/断线恢复；真实 p95 与失败率 |
| 一键复现 | Compose、Dockerfile、CI、健康检查 | 配置静态通过 | 干净机器构建、启动、导入、登录、生成、答题、恢复 |
| 校园功能保留 | AppFrontend `aiToolRoutes.test.js`；AppWeb 地图/题库测试 | 可见入口未被 Python 主线替换 | 演示一条校园 Leader 路径，确认地图/食堂/课表至少一条真实数据 |

## 明确未执行

以下项目没有证据，不能在 PPT、视频或答辩中写成“已通过”：

- 真实 MaxKB Python 知识库导出、哈希、恢复导入和文档/分段统计。
- 真实文本/视觉/视频模型连通与多模态生成。
- Docker 五服务实际构建、启动和健康冒烟。
- 30 题在线事实指标与 5×50 在线压测指标。
- AppFrontend 真机/微信开发者工具、AppWeb 浏览器端完整 E2E 和无障碍检查。
- secret/PII 扫描、传递依赖 SBOM、许可证文本包和漏洞扫描。
- 试卷 opt-in 视觉矩阵在最终平台重跑。
- 最终 PPT/PDF、7 分钟视频、字幕与冻结演示包。

## 证据提交链

| 提交 | 证据 |
| --- | --- |
| `a546ba7` | 恢复 Python 严格质量门禁 |
| `8bf005b` | Java H2 测试隔离、AppWeb lint/build 稳定 |
| `10984c5` | 地图 Drawer 生命周期回归 |
| `977281d` | 五服务 Compose、内部令牌、环境地址、CI 与一键门禁 |
| `62a82bd` | 知识库 `needs_export` 清单与校验器 |
| `9dec9c8` | 30 题事实评测与 `not_run` 报告 |
| `90a0beb` | 5×50 压测与 `not_run` 报告 |
| `01759a1` | 开源、安全、隐私、AI Coding 披露 |
