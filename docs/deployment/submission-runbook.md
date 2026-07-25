# A3 提交环境复现手册

本手册对应 `deploy/compose.submission.yml`。它启动 MySQL、Redis、Java 后端、Python AI Server 和 AppWeb 五个服务，并将 Java→Python、Python→Java、Python→Redis 的地址固定到 Compose 内部网络。

## 1. 前置条件

- Docker Engine 24+ 与 Docker Compose v2。
- 首次构建需要访问容器镜像仓库、Maven、npm 和 PyPI；镜像与依赖已缓存后，运行阶段不需要重新下载。
- 默认对宿主机开放 `3000`，MySQL、Redis、Java 和 AI 端口只绑定 `127.0.0.1`。
- MaxKB 和模型服务是外部依赖，不打包凭据，也不伪造调用结果。

## 2. 生成本地配置

```bash
cp deploy/.env.example deploy/.env
openssl rand -hex 32
openssl rand -hex 32
```

把两次输出分别填入 `JWT_SECRET` 和 `AI_INTERNAL_TOKEN`，并替换 `MYSQL_ROOT_PASSWORD`。三个值均为必填；Compose 使用 `${VAR:?}` 拒绝空值，启动前的 `config-guard` 还会拒绝 `CHANGE_ME` 和过短令牌。`deploy/.env` 已由仓库根 `.gitignore` 排除，禁止提交。

`AI_INTERNAL_TOKEN` 在 Compose 中以同一值注入 Java 与 Python。Python 会拒绝没有 `X-AI-Internal-Token` 的 `/internal/**` 请求；健康检查 `/healthz` 不受影响。本地开发未配置该变量时，Java 与 Python 使用同一个开发默认值 `dev-internal-token-change-me-32chars`，生产 Compose 仍由 `config-guard` 强制提供真实非空令牌。

GitHub 发布工作流与服务器脚本也只使用本清单。工作流以提交 SHA 作为 `IMAGE_TAG`，默认把三个镜像推送到 ACR；如镜像路径不同，可设置仓库变量 `BACKEND_IMAGE`、`AI_SERVER_IMAGE`、`WEB_IMAGE` 覆盖。服务器上的 `deploy/.env` 仍是手工复现的来源，CI 注入的镜像、`JWT_SECRET`、`AI_INTERNAL_TOKEN`、MySQL 密码按 Compose 规则优先覆盖同名值。部署脚本默认在验收通过后执行 Docker 镜像与 build cache 清理，`DEPLOY_PRUNE_UNTIL` 默认 `24h`，可按磁盘和回滚需求调整；该清理不删除 volume。

AppWeb 本地开发默认 `VITE_API_MODE=local`，浏览器请求 `http://localhost:8080`。Docker 生产镜像默认 `VITE_API_MODE=relative`，浏览器请求当前站点的 `/api/**`，再由 Nginx 代理到 Java；Java 调 Python 仍使用 Compose 内网 `http://ai-server:8081`。

## 3. 构建与启动

```bash
docker compose --env-file deploy/.env -f deploy/compose.submission.yml config --quiet
docker compose --env-file deploy/.env -f deploy/compose.submission.yml up -d --build
docker compose --env-file deploy/.env -f deploy/compose.submission.yml ps
bash deploy/verify.sh
```

`verify.sh` 只检查真实 HTTP 响应和状态，不生成模拟数据：

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/actuator/readiness`（数据库、Redis、Java→Python 内部令牌链路）
- `http://localhost:8081/healthz`
- `http://localhost:8081/internal/readiness`（传入 `AI_INTERNAL_TOKEN` 时直接复核 Python→Redis）
- `http://localhost:8081/internal/models/providers`（同时传入 `AI_INTERNAL_TOKEN` 与 `SMOKE_TOKEN` 时复核 Python 内部模型目录接口）
- `http://localhost:3000`
- `http://localhost:8080/api/auth/current-user`（无令牌时必须返回 401；设置 `SMOKE_TOKEN` 时必须完成真实鉴权请求）

若修改端口，可通过 `BACKEND_BASE_URL`、`AI_BASE_URL`、`WEB_BASE_URL` 覆盖验收地址。要执行完整内部与登录态探针，可运行：

```bash
AI_INTERNAL_TOKEN='与 deploy/.env 一致的值' \
SMOKE_TOKEN='真实学生 JWT' \
bash deploy/verify.sh
```

GitHub 自动部署同样会转发可选的 `SMOKE_TOKEN` secret；配置后每次部署都会额外验证 Python AI Server 的模型服务商目录接口。

## 4. 外部 MaxKB 与模型配置

容器启动成功不等于外部 AI 能力已经可用。以下配置必须使用真实测试环境凭据完成：

1. 以管理员身份进入 AppWeb。
2. 在知识库管理中新增 MaxKB 账号，填写 Base URL、OpenAPI Key 和 Workspace ID，执行“连接测试”。
3. 导入或选择 Python 课程知识库；根据 `artifacts/knowledge-base/python-course/README.md` 核对来源与版本。
4. 在模型配置页保存文本/视觉模型配置并执行连通测试。
5. 将所需智能体绑定到已通过测试的 `ai.service.*` 模型配置。

`deploy/.env.example` 中的 `MAXKB_*`、`LLM_*` 仅作为交接清单，不会被镜像自动消费。真实密钥存入现有管理员配置流程，不能写入 Git、截图、日志或评测数据。

没有真实凭据时，事实评测与压测脚本会输出 `not_run`，不会生成虚假准确率或延迟。

## 5. 数据初始化说明

空数据库由 Hibernate 创建表结构。仓库中的 `AppBackend/src/main/resources/data.sql` 会清空部分表，仅用于既有开发数据，提交 Compose 明确设置 `SPRING_SQL_INIT_MODE=never`，不会自动执行该破坏性脚本。

演示账号和 Python 课程数据应使用团队审核后的数据库备份或可重复执行的非破坏性初始化流程导入。不得为了“开箱数字”在启动脚本中伪造学习记录、测试成绩或知识库统计。

## 6. 本地质量门禁

依赖已经安装时执行：

```bash
bash scripts/ci/quality-gate.sh
```

门禁依次运行 Java、Python、AppWeb、AppFrontend 测试与构建，再验证 Compose 清单。部分测试会在 `127.0.0.1` 建立临时回环服务；它们不会访问互联网，但受限沙箱可能把回环端口也标记为“网络权限”。

## 7. 停止与清理

```bash
docker compose --env-file deploy/.env -f deploy/compose.submission.yml down
```

默认保留数据库、Redis、上传文件和生成附件。只有明确确认不再需要数据时，才执行带 `--volumes` 的清理命令。

自动部署脚本默认执行：

```bash
docker image prune -af --filter "until=${DEPLOY_PRUNE_UNTIL:-24h}"
docker builder prune -af --filter "until=${DEPLOY_PRUNE_UNTIL:-24h}"
```

如需临时保留所有旧镜像，可在 GitHub 仓库变量或服务器环境中设置 `DEPLOY_PRUNE_DOCKER=false`。不要在自动部署中加入 `--volumes`。

## 8. 常见故障

- `backend` 不健康：先查看 `docker compose ... logs backend mysql`，确认 MySQL 密码一致且 Java 内存足够。
- `backend dependencies` 不健康：读取 `/actuator/readiness` 的 `components`，分别定位数据库、Redis 或 `aiServer`，响应不会回显连接串和令牌。
- `ai-server` 返回 401：确认 Java 与 Python 的 `AI_INTERNAL_TOKEN` 完全一致；不要把用户 JWT 当作内部令牌。
- AppWeb `/api/**` 返回 502：确认 Compose 中 backend 的 `smart-campus-backend` 网络别名存在。
- Redis 不可用：确认 AI 容器内 `REDIS_URL=redis://redis:6379/0`；服务会降级到进程内存，但跨实例会话不能保证。
- AI 生成失败：先检查 MaxKB 与模型连通测试；不要通过本地兜底文本掩盖凭据缺失。
