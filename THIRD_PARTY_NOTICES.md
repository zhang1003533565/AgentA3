# 第三方组件清单

## 范围与证据

本清单依据当前仓库的 `pom.xml`、`requirements.txt`、`package.json`、`package-lock.json`、本机已解析包元数据和 Dockerfile 整理，覆盖直接依赖与基础镜像。它不是法律意见，也不替代各发行包中的完整许可证文本。

版本说明：

- Java 的 Spring Boot 管理版本来自 4.0.3 BOM；显式版本以 `pom.xml` 为准。
- AppWeb 的版本来自 `package-lock.json`/本地 `npm ls`，不是 `package.json` 的范围下限。
- AppFrontend 没有锁文件，`compressorjs` 只能记录声明范围；这是发布阻断项。
- Python 版本来自精确锁定的 `requirements.txt`。

## Java / AppBackend

| 组件 | 版本 | 来源 | 许可证/状态 | 用途 |
| --- | --- | --- | --- | --- |
| Spring Boot starters（JPA、WebMVC、WebSocket、Validation、WebFlux） | 4.0.3 | https://github.com/spring-projects/spring-boot | Apache-2.0 | Web、JPA、校验、SSE/WebClient |
| MySQL Connector/J | 9.6.0 | https://github.com/mysql/mysql-connector-j | GPL-2.0 with Universal FOSS Exception 1.0 | MySQL 运行时驱动 |
| Project Lombok | 1.18.42 | https://github.com/projectlombok/lombok | MIT | 编译期代码生成，不打入运行包 |
| JJWT（api/impl/jackson） | 0.12.5 | https://github.com/jwtk/jjwt | Apache-2.0 | JWT 签发与解析 |
| springdoc-openapi | 2.8.4 | https://github.com/springdoc/springdoc-openapi | Apache-2.0 | OpenAPI/Swagger UI |
| Aliyun OSS Java SDK | 3.17.4 | https://github.com/aliyun/aliyun-oss-java-sdk | Apache-2.0 | 可选对象存储 |
| Tencent COS Java SDK | 5.6.263 | https://github.com/tencentyun/cos-java-sdk-v5 | **待确认：本地 POM 未给出 SPDX 许可证** | 可选对象存储；发布前确认并附许可证 |
| Microsoft Playwright for Java | 1.49.0 | https://github.com/microsoft/playwright-java | Apache-2.0 | 浏览器自动化 |
| Apache POI OOXML | 5.5.1 | https://poi.apache.org/ | Apache-2.0 | DOCX/XLSX/PPTX 处理 |
| Apache PDFBox | 3.0.5 | https://pdfbox.apache.org/ | Apache-2.0 | PDF 处理 |
| H2 Database | 2.4.240 | https://github.com/h2database/h2database | MPL-2.0 OR EPL-1.0 | 仅测试 |
| Spring Boot test starters | 4.0.3 | https://github.com/spring-projects/spring-boot | Apache-2.0 | 仅测试 |

## Python / ai-servers

| 组件 | 版本 | 来源 | 许可证/状态 | 用途 |
| --- | --- | --- | --- | --- |
| FastAPI | 0.115.12 | https://github.com/fastapi/fastapi | MIT | AI HTTP API |
| Uvicorn | 0.30.6 | https://github.com/encode/uvicorn | BSD-3-Clause | ASGI 服务器 |
| langchain-openai | 0.2.14 | https://github.com/langchain-ai/langchain | MIT | OpenAI 兼容模型调用 |
| langchain-core | 0.3.29 | https://github.com/langchain-ai/langchain | MIT | 消息与模型抽象 |
| redis-py | 5.2.1 | https://github.com/redis/redis-py | MIT | 会话/工作流状态 |
| Pydantic | 2.10.6 | https://github.com/pydantic/pydantic | MIT | 请求与响应模型 |
| PyMuPDF | 1.25.5 | https://github.com/pymupdf/PyMuPDF | AGPL-3.0-or-later OR Artifex commercial license | PDF 提取/转换；**发布前必须确定采用的许可路径** |
| python-docx | 1.1.2 | https://github.com/python-openxml/python-docx | MIT | Word 生成 |
| python-pptx | 1.0.2 | https://github.com/scanny/python-pptx | MIT | PPTX 生成/读取 |
| pymilvus | 2.4.9 | https://github.com/milvus-io/pymilvus | Apache-2.0 | Milvus 客户端兼容依赖 |
| setuptools | 80.9.0 | https://github.com/pypa/setuptools | MIT | Python 包运行兼容 |
| marshmallow | 3.26.1 | https://github.com/marshmallow-code/marshmallow | MIT | 序列化兼容依赖 |

`pdf2docx` 0.5.8 未被代码导入，且其包元数据声明 GPLv3；本次清理已从直接依赖删除，避免携带未使用的强互惠依赖。PDF 转换当前直接使用 PyMuPDF 与 python-docx。

## AppWeb

| 组件 | 锁定版本 | 来源 | 许可证 | 用途 |
| --- | --- | --- | --- | --- |
| @ant-design/v5-patch-for-react-19 | 1.0.3 | https://github.com/ant-design/v5-patch-for-react-19 | MIT | React 19 兼容补丁 |
| antd | 5.29.3 | https://github.com/ant-design/ant-design | MIT | 管理端组件 |
| axios | 1.13.6 | https://github.com/axios/axios | MIT | HTTP 客户端 |
| dayjs | 1.11.20 | https://github.com/iamkun/dayjs | MIT | 日期处理 |
| Apache ECharts | 6.0.0 | https://github.com/apache/echarts | Apache-2.0 | 数据可视化 |
| React / React DOM | 19.2.4 | https://github.com/facebook/react | MIT | UI 运行时 |
| react-markdown | 10.1.0 | https://github.com/remarkjs/react-markdown | MIT | Markdown 渲染 |
| react-router-dom | 7.13.1 | https://github.com/remix-run/react-router | MIT | 路由 |
| Vite | 7.3.1 | https://github.com/vitejs/vite | MIT | 构建工具 |
| @vitejs/plugin-react | 5.1.4 | https://github.com/vitejs/vite-plugin-react | MIT | React 构建插件 |
| ESLint / @eslint/js | 9.39.4 | https://github.com/eslint/eslint | MIT | 静态检查 |
| eslint-plugin-react-hooks | 7.0.1 | https://github.com/facebook/react | MIT | Hooks 规则 |
| eslint-plugin-react-refresh | 0.4.26 | https://github.com/ArnaudBarre/eslint-plugin-react-refresh | MIT | 刷新规则 |
| @types/react | 19.2.14 | https://github.com/DefinitelyTyped/DefinitelyTyped | MIT | 开发类型 |
| @types/react-dom | 19.2.3 | https://github.com/DefinitelyTyped/DefinitelyTyped | MIT | 开发类型 |
| globals | 16.5.0 | https://github.com/sindresorhus/globals | MIT | ESLint 环境变量 |

## AppFrontend

| 组件 | 版本 | 来源 | 许可证/状态 | 用途 |
| --- | --- | --- | --- | --- |
| compressorjs | `^1.3.0`（未锁定） | https://github.com/fengyuanchen/compressorjs | **待从实际安装包确认并生成锁文件** | 客户端图片压缩 |

## 容器与系统包

| 组件 | 标签/版本 | 来源 | 许可证/状态 | 用途 |
| --- | --- | --- | --- | --- |
| MySQL image | 8.4.5 | https://hub.docker.com/_/mysql | GPL-2.0；附带组件另见镜像 notices | 数据库 |
| Redis image | 7.4.2-alpine | https://hub.docker.com/_/redis | RSALv2 OR SSPLv1；不是 OSI 开源许可证 | 缓存/工作流状态 |
| Python image | 3.11.11-slim-bookworm | https://hub.docker.com/_/python | Python PSF-2.0；Debian 包各自授权 | AI 运行时 |
| Maven image | 3.9.9-eclipse-temurin-21 | https://hub.docker.com/_/maven | Maven Apache-2.0；Temurin GPL-2.0-with-Classpath-Exception | Java 构建 |
| Eclipse Temurin JRE | 21-jre-jammy | https://hub.docker.com/_/eclipse-temurin | GPL-2.0-with-Classpath-Exception | Java 运行时 |
| Node image | 20-alpine | https://hub.docker.com/_/node | Node.js MIT；Alpine 包各自授权 | Web 构建 |
| nginx image | 1.27-alpine | https://hub.docker.com/_/nginx | BSD-2-Clause；Alpine 包各自授权 | Web 运行时 |
| LibreOffice Writer | Ubuntu Jammy 仓库版本 | https://www.libreoffice.org/ | MPL-2.0 / LGPL-3.0-or-later | DOCX→PDF 预览 |
| Noto CJK fonts | Ubuntu Jammy 仓库版本 | https://github.com/notofonts/noto-cjk | OFL-1.1 | 中文文档预览 |

## 外部服务（不随仓库分发）

| 服务 | 版本 | 来源 | 条款状态 | 用途 |
| --- | --- | --- | --- | --- |
| MaxKB | 部署实例版本待填写 | 部署者提供 | 许可证/商业条款按真实实例确认 | Python 课程知识库 |
| 科大讯飞/其他模型服务 | API 版本待填写 | 管理员配置 | 专有服务条款 | 文本/多模态生成 |
| 高德、腾讯地图 | API 版本待填写 | 管理员配置 | 专有服务条款 | 地图与路线 |
| 阿里云 OSS、腾讯 COS | API/SDK 见上 | 管理员配置 | 专有服务条款 | 文件存储 |

## 未完成的发布动作

1. 从最终构建环境生成 Java、Python、npm 与容器的完整传递依赖 SBOM 和许可证文本包。
2. 确认 Tencent COS SDK 与 AppFrontend `compressorjs` 实际安装版本/许可证。
3. 为 PyMuPDF 选择并满足 AGPL 或商业许可路径。
4. 补齐 `wordpapergenerate` 模板来源与授权，未补齐前不得宣称其为团队原创或开源材料。
