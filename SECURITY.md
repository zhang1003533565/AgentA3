# 安全说明

## 支持范围

比赛提交只支持 `submission/manifest.json` 最终冻结的 Git SHA 与镜像 SHA。开发分支、未冻结模型配置和未导入知识库不属于可复现安全范围。

## 漏洞报告

请不要在公开 Issue 中提交令牌、个人信息、可利用细节或真实课程资料。团队尚未在仓库中指定安全联系人；这是发布前 P0 项，必须在最终提交清单中填写一个团队控制的私密邮箱或工单入口，并约定确认、修复和披露时限。

## 凭据边界

- `JWT_SECRET`、`AI_INTERNAL_TOKEN`、MaxKB/API Key、地图和对象存储密钥只能通过本地 `.env` 或管理员配置注入。
- Java→Python 的 `/internal/**` 在部署环境必须启用独立 `AI_INTERNAL_TOKEN`；用户 JWT 不能替代服务令牌。
- Python AI Server 与 Java/Redis 使用 Compose 内部 DNS，宿主端口默认绑定 `127.0.0.1`。
- 日志、评测原始响应、PPT、视频和截图不得出现 Authorization、API Key、内部 capability、账号 ID 或知识库 ID。
- 发现凭据进入 Git 后，应立即吊销/轮换；删除文件但不轮换不算完成。

## 内容与文件安全

- Python 课程事实必须由已声明 MaxKB 来源支撑；资料不足时保持失败/拒答，不用模型常识伪装引用。
- 上传文件必须执行类型、大小、所有者和下载 capability 检查。
- Markdown 只进入允许节点，拒绝原始 HTML、`javascript:` 和 `data:` 链接。
- 代码实操默认禁止外部网络、子进程、凭据读取与破坏性文件系统操作；任何放宽都需要独立沙箱与审计。
- 模型输入中的课程资料属于不可信数据，不能把其中的提示注入文本当作系统指令执行。

## 部署基线

1. 替换 `.env.example` 中全部 `CHANGE_ME`。
2. 只允许 Web 入口对外，数据库、Redis、Java 和 AI 管理端按需要限制到可信网络。
3. 使用 SHA 镜像标签，不使用 `latest`。
4. 运行 `bash scripts/ci/quality-gate.sh` 与 secret/PII 扫描。
5. 配置 TLS、反向代理请求上限、备份加密和恢复演练。
6. 使用最小权限的外部服务账号；对象存储桶默认私有。
7. 冻结并检查 `THIRD_PARTY_NOTICES.md` 中的许可证阻断项。

## 已知安全缺口

- 现有用户密码仍按原项目逻辑直接比较，尚未完成 Argon2/bcrypt 等自适应哈希迁移；不得直接用于真实生产用户。
- 部分历史开发 SQL 含演示账号和字段值；提交前必须做 secret/PII 扫描，且提交 Compose 不自动运行破坏性的 `data.sql`。
- 安全联系人、漏洞响应 SLA、自动依赖漏洞扫描和完整 SBOM 尚未冻结。
- PyMuPDF、模板来源和部分依赖许可仍需发布负责人确认。

这些缺口不能通过文档声明视为已修复；最终演示环境应限制数据与访问范围，并在商业化前完成整改。
