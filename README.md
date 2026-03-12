# 智慧校园 - 校园活动报名模块

## 项目概述

本项目是一个基于前后端分离架构的校园活动报名系统，包含移动端 App 和 Web 管理后台，共用一套后端接口与数据库。

## 技术栈

| 端 | 技术 |
|----|------|
| 移动端 App | uni-app |
| Web 管理后台 | React + Vite |
| 后端服务 | Spring Boot |
| 数据库 | MySQL |
| 缓存 | Redis |

## 项目结构

```
SmartCampus/
├── AppBackend/          # Spring Boot 后端服务
│   └── src/main/java/com/example/appbackend/
│       ├── config/      # 配置类
│       ├── controller/  # 控制层
│       ├── service/     # 业务层
│       ├── entity/      # 实体类
│       ├── dto/         # 数据传输对象
│       ├── repository/  # 数据访问层
│       └── util/        # 工具类
├── AppFrontend/         # uni-app 移动端
│   └── pages/           # 页面目录
└── AppWeb/              # React Web 管理后台
    └── src/
        ├── pages/       # 页面组件
        ├── api/         # 接口请求
        └── utils/       # 工具函数
```

## 核心功能模块

### 一、角色定义

#### 1. 学生用户
- 查看活动列表与详情
- 报名/取消报名活动
- 查看我的报名记录
- 活动签到
- 接收活动通知

#### 2. 活动发布者（老师/社团负责人）
- 发布/编辑/删除活动
- 查看报名名单
- 审核报名申请
- 发布活动通知
- 管理签到情况

#### 3. 系统管理员
- 用户管理
- 活动分类管理
- 活动审核管理
- 数据统计查看
- 系统参数配置

### 二、功能划分

#### 学生端功能（App + Web）

| 模块 | 功能 |
|------|------|
| 活动浏览 | 活动列表、搜索、分类筛选、热门推荐 |
| 活动详情 | 查看详情、剩余名额、报名截止 |
| 报名功能 | 立即报名、取消报名、状态查询 |
| 我的活动 | 报名记录、待参加、已结束 |
| 签到功能 | 二维码签到、定位签到 |
| 消息通知 | 审核通知、活动提醒、变更通知 |

#### 发布端功能（Web）

| 模块 | 功能 |
|------|------|
| 活动管理 | 创建、编辑、删除、上下架 |
| 报名管理 | 查看名单、审核、批量操作、导出 |
| 签到管理 | 生成二维码、查看名单、补签、导出 |
| 通知管理 | 发布通知、修改公告 |

#### 后台管理端功能（Web）

| 模块 | 功能 |
|------|------|
| 用户管理 | 学生/老师管理、角色分配、账号启停 |
| 分类管理 | 活动分类增删改 |
| 活动审核 | 审核发布、驳回、状态查看 |
| 数据统计 | 活动数、报名人数、签到率、分类统计 |
| 系统配置 | 轮播图、推荐活动、规则配置 |

## 数据库设计

### 核心数据表

#### 1. 用户表 (sys_user)
存储所有用户信息，App 和 Web 共用。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR | 账号 |
| password | VARCHAR | 密码 |
| real_name | VARCHAR | 真实姓名 |
| student_no | VARCHAR | 学号 |
| phone | VARCHAR | 手机号 |
| email | VARCHAR | 邮箱 |
| gender | TINYINT | 性别 |
| avatar | VARCHAR | 头像URL |
| role_id | BIGINT | 角色ID |
| college | VARCHAR | 学院 |
| major | VARCHAR | 专业 |
| class_name | VARCHAR | 班级 |
| status | TINYINT | 状态(启用/禁用) |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

#### 2. 角色表 (sys_role)
控制用户权限。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| role_name | VARCHAR | 角色名称 |
| role_code | VARCHAR | 角色编码 |
| remark | VARCHAR | 备注 |

#### 3. 活动分类表 (activity_category)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| category_name | VARCHAR | 分类名称 |
| sort | INT | 排序 |
| status | TINYINT | 状态 |

#### 4. 活动表 (activity)
核心表，存储活动信息。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| title | VARCHAR | 活动标题 |
| cover_image | VARCHAR | 封面图 |
| category_id | BIGINT | 分类ID |
| organizer_id | BIGINT | 发布人ID |
| organizer_name | VARCHAR | 组织者名称 |
| content | TEXT | 活动详情 |
| location | VARCHAR | 活动地点 |
| max_people | INT | 人数上限 |
| current_people | INT | 当前报名人数 |
| start_time | DATETIME | 活动开始时间 |
| end_time | DATETIME | 活动结束时间 |
| signup_start_time | DATETIME | 报名开始时间 |
| signup_end_time | DATETIME | 报名结束时间 |
| status | TINYINT | 活动状态 |
| need_audit | TINYINT | 是否需要审核 |
| sign_in_type | TINYINT | 签到方式 |
| score | DECIMAL | 活动学分/积分 |
| contact_name | VARCHAR | 联系人 |
| contact_phone | VARCHAR | 联系电话 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**活动状态流转：**
```
草稿 → 待审核 → 报名中 → 报名结束 → 进行中 → 已结束
                    ↓
                  已取消
```

#### 5. 活动报名表 (activity_registration)
记录用户报名情况。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| activity_id | BIGINT | 活动ID |
| user_id | BIGINT | 用户ID |
| real_name | VARCHAR | 姓名 |
| student_no | VARCHAR | 学号 |
| phone | VARCHAR | 手机号 |
| status | TINYINT | 报名状态 |
| signup_time | DATETIME | 报名时间 |
| audit_time | DATETIME | 审核时间 |
| audit_by | BIGINT | 审核人 |
| remark | VARCHAR | 备注 |

**报名状态：** 待审核 / 已通过 / 已拒绝 / 已取消 / 已签到 / 缺席

#### 6. 活动签到表 (activity_sign_in)
记录签到情况。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| activity_id | BIGINT | 活动ID |
| registration_id | BIGINT | 报名ID |
| user_id | BIGINT | 用户ID |
| sign_in_time | DATETIME | 签到时间 |
| sign_in_type | TINYINT | 签到方式 |
| sign_in_status | TINYINT | 签到状态 |
| location_info | VARCHAR | 定位信息 |
| device_info | VARCHAR | 设备信息 |

### 扩展数据表（可选）

#### 7. 活动通知表 (activity_notice)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| activity_id | BIGINT | 活动ID |
| title | VARCHAR | 通知标题 |
| content | TEXT | 通知内容 |
| publish_by | BIGINT | 发布人 |
| publish_time | DATETIME | 发布时间 |

#### 8. 附件表 (activity_attachment)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| activity_id | BIGINT | 活动ID |
| file_name | VARCHAR | 文件名 |
| file_url | VARCHAR | 文件URL |
| file_type | VARCHAR | 文件类型 |
| upload_time | DATETIME | 上传时间 |

#### 9. 收藏表 (activity_favorite)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户ID |
| activity_id | BIGINT | 活动ID |
| create_time | DATETIME | 收藏时间 |

#### 10. 评论表 (activity_comment)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| activity_id | BIGINT | 活动ID |
| user_id | BIGINT | 用户ID |
| content | TEXT | 评论内容 |
| score | INT | 评分 |
| create_time | DATETIME | 创建时间 |

### 表关系

```
sys_user 1:N activity (发布者)
sys_user 1:N activity_registration (报名者)
sys_user 1:N activity_sign_in (签到者)

activity_category 1:N activity (分类)

activity 1:N activity_registration (报名记录)
activity 1:N activity_sign_in (签到记录)
activity 1:N activity_notice (通知)
activity 1:N activity_attachment (附件)

activity_registration 1:1 activity_sign_in (报名与签到)
```

## 关键业务逻辑

### 1. 报名校验

报名时需要验证：
- [x] 用户是否已登录
- [x] 是否重复报名
- [x] 活动是否存在且有效
- [x] 是否在报名时间内
- [x] 名额是否已满
- [x] 是否符合报名条件

### 2. 并发控制

防止超卖（名额负数）的解决方案：
- 数据库乐观锁（版本号控制）
- Redis 分布式锁
- 报名时的原子更新操作

### 3. 状态流转

**活动状态：**
```
草稿(DRAFT) → 待审核(PENDING) → 报名中(SIGNUP) → 报名结束(SIGNUP_END) → 进行中(ONGOING) → 已结束(ENDED)
                                              ↓
                                           已取消(CANCELLED)
```

**报名状态：**
```
待审核(PENDING) → 已通过(APPROVED) → 已签到(SIGNED)
      ↓                ↓
   已拒绝(REJECTED)  已取消(CANCELLED)
```

## 开发计划

### 第一阶段（MVP版本）

#### 后端接口
- [ ] 用户登录/注册
- [ ] 活动列表查询
- [ ] 活动详情查看
- [ ] 活动报名/取消报名
- [ ] 报名名单查看
- [ ] 报名审核

#### 数据库
- [ ] sys_user
- [ ] sys_role
- [ ] activity_category
- [ ] activity
- [ ] activity_registration
- [ ] activity_sign_in

#### uni-app 学生端
- [ ] 登录页面
- [ ] 活动列表页面
- [ ] 活动详情页面
- [ ] 我的报名页面

#### React 管理后台
- [ ] 登录页面
- [ ] 活动管理页面
- [ ] 分类管理页面
- [ ] 报名名单页面
- [ ] 审核管理页面

### 第二阶段（功能完善）

- [ ] 签到功能（二维码/定位）
- [ ] 活动通知推送
- [ ] 数据统计报表
- [ ] 附件上传下载
- [ ] 活动收藏功能
- [ ] 评论评分功能

### 第三阶段（高级功能）

- [ ] 活动证书生成
- [ ] 学分/积分系统
- [ ] 请假功能
- [ ] 活动推荐算法
- [ ] 数据大屏可视化
- [ ] 审批流配置

## 接口规范

### 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### 状态码定义

| 状态码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 400 | 请求参数错误 |
| 401 | 未授权/登录过期 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 业务逻辑错误（自定义） |

## 部署说明

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+

### 启动步骤

1. **启动 MySQL 和 Redis**
2. **启动后端服务**
   ```bash
   cd AppBackend
   mvn spring-boot:run
   ```
3. **启动 Web 管理后台**
   ```bash
   cd AppWeb
   npm install
   npm run dev
   ```
4. **使用 HBuilderX 运行 uni-app**

## 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 许可证

[MIT](LICENSE)

## 联系方式

如有问题或建议，欢迎提交 Issue 或联系项目维护者。

---

**最后更新：** 2026年3月
