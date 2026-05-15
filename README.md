# 智慧校园

智慧校园是一个面向校园生活服务场景的前后端分离项目，包含 uni-app 移动端、React 管理后台和 Spring Boot 后端服务。系统围绕校园用户的日常使用路径，提供首页信息聚合、校园地图、通知公告、课程表、成绩查询、论坛社区、活动报名、失物招领、宿舍服务、餐饮商户、优惠活动、运动场馆、消息中心和 AI 创作等功能。

项目采用一套后端接口同时支撑移动端 App 和 Web 管理后台，后端负责用户认证、业务数据管理、地图服务、文件上传、AI 对话/写作配置、统计分析等能力。

## 项目截图

> 截图可以放到 `docs/images/` 目录，然后替换下面的图片路径。

### 移动端首页

![移动端首页](docs/images/app-home.png)

### 校园地图

![校园地图](docs/images/app-map.png)

### AI 创作

![AI 创作](docs/images/app-ai.png)

### Web 管理后台

![Web 管理后台](docs/images/web-dashboard.png)

## 功能模块

### 移动端 App

- 首页：校园服务入口、轮播信息、快捷功能聚合。
- 校园地图：地点标记、附近设施、路线导航、位置检索。
- 通知公告：公告列表、公告详情、消息提醒。
- 课程与成绩：课程表、课程详情、成绩查询。
- 社区论坛：话题、帖子、评论、点赞、关注、个人主页。
- 校园活动：活动列表、活动详情、报名记录、我的活动。
- 失物招领：物品发布、详情查看、聊天沟通、我的发布。
- 宿舍服务：宿舍列表、宿舍选择、宿舍详情。
- 餐饮与商户：餐厅、档口、菜品、评价。
- 优惠活动：优惠列表、搜索、详情、领取。
- 运动场馆：场馆列表、场馆详情。
- AI 功能：智能写作、AI 创作、对话记忆。
- 个人中心：登录注册、资料维护、密码修改。

### Web 管理后台

- 后台登录与权限入口。
- 用户管理。
- 首页数据看板和统计分析。
- 公告、活动、商户、菜品、设施、地图点位等业务数据维护。
- 系统配置、上传配置、AI 配置等管理能力。

### 后端服务

- Spring Boot REST API。
- JWT 登录认证。
- MySQL 数据持久化。
- Redis 缓存与会话辅助能力。
- 文件上传、本地存储、OSS/COS 云存储接入。
- 腾讯地图、高德地图相关接口接入。
- DeepSeek/大模型配置与 AI 写作能力。
- Swagger 接口文档。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 移动端 App | uni-app |
| Web 管理后台 | React 19、Vite、Ant Design、ECharts、Axios |
| 后端服务 | Spring Boot 4、Spring Web MVC、Spring Data JPA、Spring Data Redis |
| 数据库 | MySQL |
| 缓存 | Redis |
| 接口文档 | Springdoc OpenAPI / Swagger UI |
| 地图服务 | 腾讯地图、高德地图 |
| 文件存储 | 本地上传、阿里云 OSS、腾讯云 COS |
| AI 能力 | DeepSeek、大模型对话/写作配置 |

## 项目结构

```text
smart-campus/
├── AppBackend/          # Spring Boot 后端服务
│   └── src/main/
│       ├── java/com/example/appbackend/
│       │   ├── config/      # 配置类
│       │   ├── controller/  # 控制层接口
│       │   ├── dto/         # 数据传输对象
│       │   ├── entity/      # JPA 实体
│       │   ├── graph/       # AI 对话状态
│       │   ├── repository/  # 数据访问层
│       │   ├── scheduler/   # 定时任务
│       │   ├── service/     # 业务层
│       │   └── util/        # 工具类
│       └── resources/
│           ├── application.yml
│           └── data.sql
├── AppFrontend/         # uni-app 移动端
│   ├── pages/           # 主包页面
│   ├── subpackage_*/    # 分包页面
│   ├── api/             # 接口封装
│   ├── components/      # 公共组件
│   ├── static/          # 静态资源
│   └── utils/           # 工具与配置
├── AppWeb/              # React Web 管理后台
│   ├── src/
│   │   ├── api/         # 后台接口
│   │   ├── components/  # 公共组件
│   │   ├── pages/       # 页面
│   │   └── utils/       # 请求与存储工具
│   └── vite.config.js
└── README.md
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- MySQL 8+
- Redis 6+
- HBuilderX 或 uni-app 运行环境

## 数据库创建

本项目使用 MySQL，数据库名称为：

```text
smart-campus
```

先启动 MySQL，然后执行：

```sql
CREATE DATABASE `smart-campus` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

如果需要手动选择数据库：

```sql
USE `smart-campus`;
```

后端启动时会根据 JPA 实体自动创建或更新表结构，并执行 `AppBackend/src/main/resources/data.sql` 初始化基础数据。

后端默认连接配置位于 `AppBackend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smart-campus?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
```

如本地数据库账号不同，请修改为自己的 MySQL 用户名和密码。

## 启动后端服务

后端目录为 `AppBackend/`，是一个 Spring Boot + Maven 项目。

### 1. 启动 Redis

默认 Redis 地址为：

```text
localhost:6379
```

如需修改，可以通过环境变量覆盖：

```bash
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=
export REDIS_DATABASE=0
```

### 2. 配置第三方服务

地图、对象存储、AI 等配置在 `AppBackend/src/main/resources/application.yml` 中维护。正式运行时建议使用自己的服务密钥，并避免把真实密钥提交到 Git 仓库。

涉及的配置包括：

- 腾讯地图 Key
- 高德地图 Key
- 阿里云 OSS
- 腾讯云 COS
- DeepSeek API Key

### 3. 运行后端

```bash
cd AppBackend
mvn spring-boot:run
```

启动成功后，后端服务地址为：

```text
http://localhost:8080
```

Swagger 接口文档地址：

```text
http://localhost:8080/swagger-ui.html
```

### 4. 后端创建方式

如果需要从零创建同类型后端项目，可以使用 Spring Initializr 创建 Maven 项目：

- Project：Maven
- Language：Java
- Spring Boot：4.x
- Java：21
- Dependencies：Spring Web、Spring Data JPA、Spring Data Redis、MySQL Driver、Validation、Lombok

创建后把业务代码放到类似结构中：

```text
src/main/java/com/example/appbackend/
├── config/
├── controller/
├── dto/
├── entity/
├── repository/
├── service/
└── util/
```

## 启动 Web 管理后台

Web 管理后台目录为 `AppWeb/`，是一个 React + Vite 项目。

```bash
cd AppWeb
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5174
```

构建生产包：

```bash
npm run build
```

本地预览生产包：

```bash
npm run preview
```

### Web 管理后台创建方式

如果需要从零创建同类型 Web 项目，可以执行：

```bash
npm create vite@latest AppWeb -- --template react
cd AppWeb
npm install
npm install antd axios dayjs echarts react-router-dom
npm run dev
```

当前项目固定使用 Vite 端口 `5174`，配置在 `AppWeb/package.json`：

```json
{
  "scripts": {
    "dev": "vite --port 5174"
  }
}
```

## 启动移动端 App

移动端位于 `AppFrontend/`，推荐使用 HBuilderX 打开并运行。

### 方式一：HBuilderX 运行

1. 使用 HBuilderX 打开 `AppFrontend/`。
2. 确认后端服务已启动。
3. 检查 `AppFrontend/utils/config.js` 中的接口地址是否指向本机后端。
4. 选择运行到浏览器、微信小程序模拟器或手机设备。

### 方式二：安装前端依赖

```bash
cd AppFrontend
npm install
```

该目录主要作为 uni-app 工程使用，具体运行方式以 HBuilderX 或当前 uni-app 工具链为准。

### App 创建方式

如果需要从零创建同类型 App 项目：

1. 打开 HBuilderX。
2. 选择 `文件` -> `新建` -> `项目`。
3. 选择 `uni-app` 项目。
4. 项目名称填写 `AppFrontend`。
5. 模板可选择默认模板。
6. 创建完成后，将页面放到 `pages/` 或 `subpackage_*` 分包目录。
7. 在 `utils/config.js` 中配置后端接口地址。

当前移动端接口地址配置示例：

```js
export const BASE_URL = 'http://localhost:8080'
```

如果运行到手机真机，不能使用 `localhost`，需要改成电脑局域网 IP，例如：

```js
export const BASE_URL = 'http://192.168.1.100:8080'
```

## 常用访问地址

| 服务 | 地址 |
| --- | --- |
| 后端 API | http://localhost:8080 |
| Swagger 文档 | http://localhost:8080/swagger-ui.html |
| Web 管理后台 | http://localhost:5174 |
| MySQL 数据库 | localhost:3306/smart-campus |
| Redis | localhost:6379 |

## 开发说明

- 后端首次启动时会根据 JPA 实体自动更新表结构，并执行 `data.sql` 初始化数据。
- 移动端和管理后台都依赖后端 API，调试前请先启动后端服务。
- 如果 App 运行在真机，需要把接口地址从 `localhost` 改为电脑在局域网中的 IP 地址。
- 上传文件默认会写入后端的 `uploads/` 目录，也可以按配置切换到 OSS/COS。
- 项目中涉及第三方平台的 Key 建议使用环境变量或本地私有配置管理，避免提交真实密钥。

## GitHub 推送说明

如果 GitHub 提示 Push Protection 拦截，说明提交内容或历史提交中包含疑似密钥。即使是测试密钥，也需要在 GitHub 页面放行，或清理 Git 历史后重新推送。

当前仓库远程地址示例：

```bash
git remote set-url origin git@github.com:zhang1003533565/AgentA3.git
git push -u origin master
```
